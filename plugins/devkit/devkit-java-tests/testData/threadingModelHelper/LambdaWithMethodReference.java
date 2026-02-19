import com.intellij.util.concurrency.ThreadingAssertions;
import java.util.Arrays;
import java.util.List;

class LambdaWithMethodReference {
  void testMethod() {
    List<String> items = Arrays.asList("a", "b", "c");
    items.forEach(this::processItem);
  }

  void processItem(String item) {
    ThreadingAssertions.assertReadAccess();
  }
}