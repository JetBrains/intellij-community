import testutils.RequiresReadLock;
import testutils.ExpectedPath

@ExpectedPath("AnnotationInChain.testMethod -> AnnotationInChain.intermediateMethod -> " +
              "AnnotationInChain.targetMethod -> @RequiresReadLock")
class AnnotationInChain {
  void testMethod() {
    intermediateMethod();
  }

  void intermediateMethod() {
    targetMethod();
  }

  @RequiresReadLock
  void targetMethod() {
  }
}