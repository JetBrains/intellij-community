public class C {
  public void testMessageBusDetection() {
    com.intellij.util.messages.MessageBus mb = null;
    mb.syncPublisher(MyTopic.class);
  }
}
interface MyTopic {}