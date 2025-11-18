import com.intellij.util.concurrency.ThreadingAssertions;

class AssertionInNestedBlock {
  void testMethod() {
    if (true) {
      for (int i = 0; i < 10; i++) {
        ThreadingAssertions.assertReadAccess();
      }
    }
  }