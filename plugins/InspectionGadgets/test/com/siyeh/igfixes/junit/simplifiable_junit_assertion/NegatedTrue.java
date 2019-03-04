import static org.junit.Assert.assertTrue;

class MyTest {

  @Test
  public void testObjectsEquals() {
      <caret>assertTrue(!(1 == 2));
  }
}