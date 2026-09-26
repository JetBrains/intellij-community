import com.intellij.util.concurrency.ThreadingAssertions

class SuspendAndProperty {
  val fromProperty: Int
    get() {
      ThreadingAssertions.assertReadAccess()
      return 1
    }

  fun susp() {
    ThreadingAssertions.assertReadAccess()
  }

  fun testMethod() {
    val x = fromProperty
    susp()
  }
}
