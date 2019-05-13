import junit.framework.TestCase;

class SetupCallsSuperSetup extends TestCase {

  protected void <warning descr="Method 'setUp()' does not call 'super.setUp()'">setUp</warning>() throws Exception {
    System.out.println("foo");
  }
}
class OK extends TestCase {
  protected void setUp() throws Exception {
    super.setUp();
  }
}
class NotCalled2 extends TestCase {
  protected void <warning descr="Method 'setUp()' does not call 'super.setUp()'">setUp</warning>() throws Exception {
    if (false) super.setUp();
  }
}
class Suppressed extends TestCase {
  @SuppressWarnings("SetUpDoesntCallSuperSetUp")
  protected void setUp() throws Exception {
  }
}
class Lambda extends TestCase {
  protected void tearDown() throws Exception {
    Runnable r = () -> { try { super.tearDown(); } catch (Exception e) {} };
    r.run();
  }
}