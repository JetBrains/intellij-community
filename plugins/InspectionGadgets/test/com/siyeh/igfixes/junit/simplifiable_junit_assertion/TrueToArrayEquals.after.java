import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

class ObjectEqualsToEquals {

  @Test
  public void testObjectsEquals() {
      <caret>assertArrayEquals(getFoo(), getBar());
  }

  int[] getFoo() { return "foo"; }
  int[] getBar() { return "foo"; }
}