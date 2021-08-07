import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class Test {

  private Stream<Arguments> parmeters() {
    return null;
  }

  @MethodSource("parm<caret>eters")
  @ParameterizedTest
  void foo(String param) {}
}