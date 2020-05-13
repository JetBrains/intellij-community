import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class Test {
    private static Stream<Arguments> parmeters() {
        return null;
    }

    @MethodSource("parmeters")
  @ParameterizedTest
  void foo(String param) {}
}