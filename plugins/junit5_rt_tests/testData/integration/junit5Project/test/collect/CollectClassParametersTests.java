package collect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test data for collecting the parameters of a method that itself runs once per parameter set of its class.
 * As in {@link CollectParametersTests}, a body that runs appends to the marker file, which the collect pass must leave empty.
 */
@ParameterizedClass
@ValueSource(strings = {"radar", "level"})
public class CollectClassParametersTests {
  @Parameter
  String candidate;

  @ParameterizedTest
  @ValueSource(ints = {1, -3})
  public void parameterized(int number) {
    CollectParametersTests.recordBodyExecution();
  }

  /** A test without parameters of its own. It still runs once per parameter set of the class, and a dry run must not run its body. */
  @Test
  public void plain() {
    CollectParametersTests.recordBodyExecution();
  }
}
