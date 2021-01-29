package disabled;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

class DisabledMethodIf {

  @Test
  void testShouldBeExecuted() {
  }
  @Test
  @DisabledIf("foo")
  void testDisabledMethod() {
  }

  boolean foo() {
    return true;
  }
}