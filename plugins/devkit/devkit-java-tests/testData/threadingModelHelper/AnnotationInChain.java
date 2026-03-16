import com.intellij.util.concurrency.annotations.RequiresReadLock;

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