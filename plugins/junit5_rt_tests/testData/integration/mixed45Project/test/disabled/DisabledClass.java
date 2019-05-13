package disabled;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

@Disabled("Class disabled")
class DisabledClass {

  @Test
  void testShouldBeExecuted() {
  }

  @Test
  @Disabled("Method disabled")
  void testDisabledMethod() {
  }
}