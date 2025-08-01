package org.jetbrains.idea.devkit.threadingModelHelper;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import testutils.ExpectedPath;

@ExpectedPath("RealWorldUI.testMethod -> Helper.helperMethod -> Service.serviceMethod -> @RequiresReadLock")
@ExpectedPath("RealWorldUI.testMethod -> Helper.helperMethod -> Service.serviceMethod -> ThreadingAssertions.assertReadAccess()")
class RealWorldInsideUI {
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



