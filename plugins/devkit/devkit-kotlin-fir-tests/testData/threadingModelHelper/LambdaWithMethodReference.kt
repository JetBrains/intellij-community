import com.intellij.util.concurrency.ThreadingAssertions

object LambdaWithMethodReference {
  fun testMethod() {
    val items = listOf(1, 2, 3)
    items.forEach { _ -> processItem() }
  }

  fun processItem() {
    ThreadingAssertions.assertReadAccess()
  }
}
