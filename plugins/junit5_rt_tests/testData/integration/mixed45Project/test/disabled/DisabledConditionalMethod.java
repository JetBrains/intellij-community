package disabled;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class DisabledConditionalMethod {

  @Test
  void testShouldBeExecuted() {
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void testDisabledMethod(String s ) {
  }
}