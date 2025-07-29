import testutils.ThreadingAssertions;
import testutils.ExpectedPath

@ExpectedPath("AnnotationInChain.testMethod -> @RequiresReadLock")
class AnnotationInChain {
  void testMethod() {
    if (true) {
      for (int i = 0; i < 10; i++) {
        ThreadingAssertions.assertReadAccess();
      }
    }
  }