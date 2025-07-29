import testutils.RequiresReadLock;
import testutils.ExpectedPath

@ExpectedPath("CyclicRecursiveCalls.testMethod -> CyclicRecursiveCalls.methodB -> @RequiresReadLock")
class CyclicRecursiveCalls {
  void testMethod() {
    methodB();
  }

  @RequiresReadLock
  void methodB() {
    methodC();
  }

  void methodC() {
    testMethod();
  }
}