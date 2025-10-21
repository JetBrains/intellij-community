import java.lang.Thread;

class UseIntelliJVirtualThreads {
  public static void main(String[] args) {
    <warning descr="Use 'IntelliJVirtualThreads.ofVirtual()' instead of 'Thread.ofVirtual()'">Thread.ofVirtual()<caret></warning>;
  }
}
