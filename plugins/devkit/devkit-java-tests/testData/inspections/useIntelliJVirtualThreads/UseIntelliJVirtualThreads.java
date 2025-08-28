import com.intellij.virtualThreads.IntelliJVirtualThreads;
import java.lang.Thread;

class UseIntelliJVirtualThreads {
  void m() {
    <warning descr="Use 'IntelliJVirtualThreads.ofVirtual()' instead of 'Thread.ofVirtual()'">Thread.ofVirtual()</warning>;
    <warning descr="Use 'IntelliJVirtualThreads.ofVirtual()' instead of 'Thread.ofVirtual()'">java.lang.Thread.ofVirtual()</warning>;
  }
  void ok() {
    IntelliJVirtualThreads.ofVirtual();
  }
}
