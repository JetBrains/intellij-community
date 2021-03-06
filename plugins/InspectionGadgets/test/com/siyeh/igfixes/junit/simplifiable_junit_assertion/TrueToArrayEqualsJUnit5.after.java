import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.*;
class ObjectEqualsToEquals {

  @Test
  public void testObjectsEquals() {
      <caret>assertArrayEquals(getFoo(), getBar(), "message");
  }

  int[] getFoo() { return new int[0]; }
  int[] getBar() { return new int[0]; }
}