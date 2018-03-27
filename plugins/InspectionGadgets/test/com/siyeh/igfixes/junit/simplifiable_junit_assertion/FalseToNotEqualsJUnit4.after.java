import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

class DoublePrimitive {

  @Test
  public void testPrimitive() {
      assertNotEquals(2.0, doubleValue(), 0.0);
  }

  Double doubleValue() {
    return 1.0;
  }
}