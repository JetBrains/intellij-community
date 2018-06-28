import static org.junit.Assert.assertTrue;

import java.util.Arrays;

class ObjectEqualsToEquals {

  @Test
  public void testObjectsEquals() {
      <caret>assertTrue(Arrays.equals(getFoo(), getBar()));
  }

  int[] getFoo() { return "foo"; }
  int[] getBar() { return "foo"; }
}