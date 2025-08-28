import com.intellij.virtualThreads.IntelliJVirtualThreads

class UseIntelliJVirtualThreadsKotlin {
  fun m(): Thread.Builder {
    return <warning descr="Use 'IntelliJVirtualThreads.ofVirtual()' instead of 'Thread.ofVirtual()'">java.lang.Thread.ofVirtual()</warning>
  }
  fun ok() {
    IntelliJVirtualThreads.ofVirtual()
  }
}
