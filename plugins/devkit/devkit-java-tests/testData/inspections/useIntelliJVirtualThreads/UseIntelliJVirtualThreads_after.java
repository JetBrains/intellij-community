import com.intellij.virtualThreads.IntelliJVirtualThreads;
import java.lang.Thread;

class UseIntelliJVirtualThreads {
  java.lang.Thread.Builder m() {
    return IntelliJVirtualThreads.ofVirtual();
  }
  void ok() {
    IntelliJVirtualThreads.ofVirtual();
  }
}
