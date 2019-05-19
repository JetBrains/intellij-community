import junit.framework.*;

public class SuiteCase extends TestCase {

  public void testBla() {
    assertFalse("message", false);
  }

  @org.junit.Test
  public void <warning descr="Method 'testForeign()' annotated with '@Test' inside class extending JUnit 3 TestCase"><caret>testForeign</warning>() {

  }

  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTest(new SuiteCase());
    return suite;
  }
}