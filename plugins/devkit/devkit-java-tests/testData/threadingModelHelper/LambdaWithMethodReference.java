import testutils.ThreadingAssertions;
import testutils.ExpectedPath
import java.util.Arrays;
import java.util.List;

@ExpectedPath("LambdaWithMethodReference.testMethod -> LambdaWithMethodReference.processItem -> ThreadingAssertions.assertReadAccess()")
class LambdaWithMethodReference {
  void testMethod() {
    List<String> items = Arrays.asList("a", "b", "c");
    items.forEach(this::processItem);
  }

  void processItem(String item) {
    ThreadingAssertions.assertReadAccess();
  }
}