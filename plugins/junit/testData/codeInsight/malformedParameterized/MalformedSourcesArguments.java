
import java.util.Iterator;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MethodSourceProblem {
  @ParameterizedTest
  @MethodSource(value = {"stream",  "iterator"})
  void test(int x, int y) {
    System.out.println(x + ", "+ y);
  }

  static Stream<Arguments> stream() {
    return null;
  }

  static Iterator<Arguments> iterator() {
    return null;
  }
}