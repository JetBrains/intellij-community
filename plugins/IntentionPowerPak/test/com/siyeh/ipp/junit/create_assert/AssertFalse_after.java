import junit.framework.TestCase;

public class AssertFalse extends TestCase {

  public void testOne() {
      assertFalse(result());
  }

  boolean result() {
    return false;
  }
}