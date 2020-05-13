import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Test {
  @MethodSource("par<caret>meters")
  @ParameterizedTest
  void foo(String param) {}
}