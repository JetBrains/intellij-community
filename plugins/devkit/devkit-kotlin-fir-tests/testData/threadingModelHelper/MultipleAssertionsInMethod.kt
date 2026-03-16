import com.intellij.util.concurrency.ThreadingAssertions

class MultipleAssertionsInMethod {
  fun testMethod() {
    ThreadingAssertions.assertReadAccess()
    ThreadingAssertions.assertReadAccess()
  }
}
