import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.*;
class ObjectEqualsToEquals {

  @Test
  public void testObjectsEquals() {
      <caret>assertTrue(Arrays.equals(getFoo(), getBar()), "message");
  }

  int[] getFoo() { return new int[0]; }
  int[] getBar() { return new int[0]; }
}