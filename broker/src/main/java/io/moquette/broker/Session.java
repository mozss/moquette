package io.moquette.broker;

import io.moquette.spi.impl.subscriptions.Subscription;
import io.moquette.spi.impl.subscriptions.Topic;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class Session {

    enum SessionStatus {
        CONNECTED, CONNECTING, DISCONNECTING, DISCONNECTED
    }

    static final class Will {

        private final String topic;
        private final byte[] payload;

        Will(String topic, byte[] payload) {
            this.topic = topic;
            this.payload = payload;
        }
    }

    private final String clientId;
    private boolean clean;
    private Will will;
//    private volatile SessionStatus status;
    private final AtomicReference<SessionStatus> status = new AtomicReference<>(SessionStatus.DISCONNECTED);
    private MQTTConnection mqttConnection;
    private List<Subscription> subscriptions = new ArrayList<>();

    Session(String clientId, boolean clean, Will will) {
        this.clientId = clientId;
        this.clean = clean;
        this.will = will;
    }

    void update(boolean clean, Will will) {
        this.clean = clean;
        this.will = will;
    }

    void markConnected() {
        assignState(SessionStatus.DISCONNECTED, SessionStatus.CONNECTED);
    }

    void bind(MQTTConnection mqttConnection) {
        this.mqttConnection = mqttConnection;
    }

    public boolean disconnected() {
        return status.get() == SessionStatus.DISCONNECTED;
    }

    public boolean connected() {
        return status.get() == SessionStatus.CONNECTED;
    }

    public String getClientID() {
        return clientId;
    }

    public List<Subscription> getSubscriptions() {
        return new ArrayList<>(subscriptions);
    }

    public Will getWill() {
        return will;
    }

    boolean assignState(SessionStatus expected, SessionStatus newState) {
        return status.compareAndSet(expected, newState);
    }

    public void closeImmediatly() {
        mqttConnection.dropConnection();
    }

    public void disconnect() {
        final boolean res = assignState(SessionStatus.CONNECTED, SessionStatus.DISCONNECTING);
        if (!res) {
            // someone already moved away from CONNECTED
            // TODO what to do?
            return;
        }

        mqttConnection = null;
        will = null;

        assignState(SessionStatus.DISCONNECTING, SessionStatus.DISCONNECTED);
    }

    boolean isClean() {
        return clean;
    }

    void sendPublishNotRetained(Topic topic, MqttQoS qos, ByteBuf payload) {
        MqttPublishMessage publishMsg = notRetainedPublish(topic.toString(), qos, payload);
        mqttConnection.sendPublish(publishMsg);
    }

    private static MqttPublishMessage notRetainedPublish(String topic, MqttQoS qos, ByteBuf message) {
        return notRetainedPublishWithMessageId(topic, qos, message, 0);
    }

    private static MqttPublishMessage retainedPublish(String topic, MqttQoS qos, ByteBuf message) {
        return retainedPublishWithMessageId(topic, qos, message, 0);
    }

    private static MqttPublishMessage notRetainedPublishWithMessageId(String topic, MqttQoS qos, ByteBuf message,
                                                                      int messageId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, false, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, messageId);
        return new MqttPublishMessage(fixedHeader, varHeader, message);
    }

    private static MqttPublishMessage retainedPublishWithMessageId(String topic, MqttQoS qos, ByteBuf message,
                                                                   int messageId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, true, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, messageId);
        return new MqttPublishMessage(fixedHeader, varHeader, message);
    }

    void sendPublishNotRetainedWithMessageId(Topic topic, MqttQoS qos, ByteBuf payload, int messageId) {
        MqttPublishMessage publishMsg = notRetainedPublishWithMessageId(topic.toString(), qos, payload, messageId);
        mqttConnection.sendPublish(publishMsg);
    }

    void sendRetainedPublish(Topic topic, MqttQoS qos, ByteBuf payload) {
        MqttPublishMessage publishMsg = retainedPublish(topic.toString(), qos, payload);
        mqttConnection.sendPublish(publishMsg);
    }

    void sendRetainedPublishWithMessageId(Topic topic, MqttQoS qos, ByteBuf payload, int messageId) {
        MqttPublishMessage publishMsg = notRetainedPublishWithMessageId(topic.toString(), qos, payload, messageId);
        mqttConnection.sendPublish(publishMsg);
    }
}