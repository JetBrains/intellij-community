import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Test {
  @MethodSource("para<caret>meters")
  @ParameterizedTest
  void foo(String param) {}
}