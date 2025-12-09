import com.intellij.util.concurrency.annotations.RequiresReadLock

class CyclicRecursiveCalls {
  fun testMethod() {
    methodA()
  }

  fun methodA() {
    methodB()
  }

  @RequiresReadLock
  fun methodB() {
    methodA()
  }
}
