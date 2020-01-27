import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Junit5Arguments {
  @MethodSource(<warning descr="Cannot resolve target method source: 'parameters'">"parameters"</warning>)
  @ParameterizedTest
  void foo(String param) {}
}