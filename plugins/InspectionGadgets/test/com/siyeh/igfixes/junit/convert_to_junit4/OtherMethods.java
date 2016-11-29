import junit.framework.TestCase;

public class OtherMethods extends TestCase {

  public void _testIgnored() {
    assertTrue(true);
  }

  public void testSomething(int i) {
    assertTrue(true);
  }

  public void testBla() {
    assertFalse(false);
  }

  @org.junit.Test
  public void <caret>testForeign() {

  }
}