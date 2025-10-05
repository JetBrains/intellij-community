import java.lang.Thread

class UseIntelliJVirtualThreadsKotlin {
  fun foo() {
    Thread.<warning descr="Use 'IntelliJVirtualThreads.ofVirtual()' instead of 'Thread.ofVirtual()'">ofV<caret>irtual()</warning>
  }
}
