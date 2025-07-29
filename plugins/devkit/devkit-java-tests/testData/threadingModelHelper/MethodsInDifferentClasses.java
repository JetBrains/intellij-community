import testutils.RequiresReadLock;
import testutils.ThreadingAssertions;
import testutils.ExpectedPath;

@ExpectedPath("MethodsInDifferentClasses.testMethod -> Helper.helperMethod -> Service.serviceMethod -> @RequiresReadLock")
@ExpectedPath("MethodsInDifferentClasses.testMethod -> Helper.helperMethod -> Service.serviceMethod -> ThreadingAssertions.assertReadAccess()")
class MethodsInDifferentClasses {
  void testMethod() {
    Helper helper = new Helper();
    helper.helperMethod();
  }

  class Helper {
    void helperMethod() {
      Service service = new Service();
      service.serviceMethod();
    }
  }

  class Service {
    @RequiresReadLock
    void serviceMethod() {
      ThreadingAssertions.assertReadAccess();
    }
  }
}

