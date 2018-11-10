package disabled;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

class DisabledMethodIf {

  @Test
  void testShouldBeExecuted() {
  }

  @Test
  @DisabledIf("2==2")
  void testDisabledMethod() {
  }
}