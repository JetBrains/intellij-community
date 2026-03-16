import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.ThreadingAssertions;

class DifferentClassesMethods {
  void testMethod() {
    Helper helper = new Helper();
    helper.helperMethod();
  }

  class Helper {
    void helperMethod() {
      Service service = new Service();
      service.serviceMethod();
      ThreadingAssertions.assertWriteAccess();
    }
  }

  class Service {
    @RequiresEdt
    void serviceMethod() {}
  }
}

