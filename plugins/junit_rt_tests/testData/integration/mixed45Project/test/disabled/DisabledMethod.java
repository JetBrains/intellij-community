package disabled;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

class DisabledMethod {

  @Test
  void testShouldBeExecuted() {
  }

  @Test
  @Disabled("Method disabled")
  void testDisabledMethod() {
  }
}