import com.intellij.util.concurrency.annotations.RequiresReadLock

object AnnotationInChain {
  fun testMethod() {
    intermediateMethod()
  }

  fun intermediateMethod() {
    targetMethod()
  }

  @RequiresReadLock
  fun targetMethod() {}
}
