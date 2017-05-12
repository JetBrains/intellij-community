import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MyJunit5 {
  @ParameterizedTest
  @ValueSource(strings = "foo")
  void testWithRegularParameterResolver(String argument, TestInfo testReporter) {
  }
  @ParameterizedTest
  <warning descr="Multiple parameters are not supported by this source">@ValueSource(strings = "foo")</warning>
  void testWithMultipleParams(String argument, int i) {
  }
}
