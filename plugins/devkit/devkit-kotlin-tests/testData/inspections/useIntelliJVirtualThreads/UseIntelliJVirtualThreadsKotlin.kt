import com.intellij.virtualThreads.IntelliJVirtualThreads

class UseIntelliJVirtualThreadsKotlin {
  fun m(): Thread.Builder {
    return java.lang.Thread.<warning descr="Use 'IntelliJVirtualThreads.ofVirtual()' instead of 'Thread.ofVirtual()'">ofVirtual()</warning>
  }
  fun ok() {
    IntelliJVirtualThreads.ofVirtual()
  }
}
