import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Test {

  private Stream<Arguments> parmeters() {
    return null;
  }

  @MethodSource("parmeters")
  @ParameterizedTest
  void foo(String param) {}
}