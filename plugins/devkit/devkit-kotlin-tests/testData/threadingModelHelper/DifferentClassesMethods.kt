import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.ThreadingAssertions

class DifferentClassesMethods {
  fun testMethod() {
    Helper().helperMethod()
  }
}

class Helper {
  fun helperMethod() {
    ThreadingAssertions.assertWriteAccess()
    Service().serviceMethod()
  }
}

class Service {
  @RequiresEdt
  fun serviceMethod() {}
}
