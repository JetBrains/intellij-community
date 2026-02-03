import com.intellij.util.concurrency.ThreadingAssertions

object AssertionInNestedBlock {
  fun testMethod() {
    if (System.currentTimeMillis() > 0) {
      ThreadingAssertions.assertReadAccess()
    }
  }
}
