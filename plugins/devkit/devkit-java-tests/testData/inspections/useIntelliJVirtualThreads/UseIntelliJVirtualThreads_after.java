import com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads;

import java.lang.Thread;

class UseIntelliJVirtualThreads {
  public static void main(String[] args) {
    IntelliJVirtualThreads.ofVirtual()<caret>;
  }
}
