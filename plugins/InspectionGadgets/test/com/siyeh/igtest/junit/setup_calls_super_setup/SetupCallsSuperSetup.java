import junit.framework.TestCase;

class SetupCallsSuperSetup extends TestCase {

  protected void <warning descr="'setUp()' does not call 'super.setUp()'">setUp</warning>() throws Exception {
    System.out.println("foo");
  }
}
class OK extends TestCase {
  protected void setUp() throws Exception {
    super.setUp();
  }
}
class NotCalled2 extends TestCase {
  protected void <warning descr="'setUp()' does not call 'super.setUp()'">setUp</warning>() throws Exception {
    if (false) super.setUp();
  }
}