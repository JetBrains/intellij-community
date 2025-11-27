import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.concurrency.ThreadingAssertions

class BothAnnotationAndAssertion {
  @RequiresWriteLock
  fun testMethod() {
    ThreadingAssertions.assertBackgroundThread()
  }
}
