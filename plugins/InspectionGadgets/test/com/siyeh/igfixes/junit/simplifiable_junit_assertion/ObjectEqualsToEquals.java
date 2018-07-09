import static org.junit.Assert.assertTrue;

import java.util.Objects;

class ObjectEqualsToEquals {

  @Test
  public void testObjectsEquals() {
      <caret>assertTrue(Objects.equals(getFoo(), getBar()));
  }

  String getFoo() { return "foo"; }
  String getBar() { return "foo"; }
}