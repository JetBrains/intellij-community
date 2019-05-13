// "Replace with 'String[]::new'" "true"
import java.util.stream.Stream;

class Test {
  void test(Stream<String> stream) {
    Integer[] integers = stream.toArray(Integer[]::<caret>new);
  }
}