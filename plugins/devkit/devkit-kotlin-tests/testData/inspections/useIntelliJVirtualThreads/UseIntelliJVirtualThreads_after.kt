import com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads
import java.lang.Thread

class UseIntelliJVirtualThreadsKotlin {
  fun foo() {
      IntelliJVirtualThreads.ofVirtual()
  }
}
