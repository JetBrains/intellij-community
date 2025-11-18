import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.ThreadingAssertions;

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