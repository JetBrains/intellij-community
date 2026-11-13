package repeat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FailsInSecondRunTests {
  private static int ourInvocations;

  @ParameterizedTest
  @ValueSource(strings = {"a", "b"})
  void parameterized(String value) {
    assertTrue(++ourInvocations < 4, "the last invocation of the second run fails");
  }
}
