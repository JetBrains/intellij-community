import static org.junit.Assert.assertFalse;

class DoublePrimitive {

  @Test
  public void testPrimitive() {
      <caret>assertFalse(doubleValue().equals(2.0));
  }

  Double doubleValue() {
    return 1.0;
  }
}