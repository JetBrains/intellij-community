import junit.framework.TestCase;

class SuperTearDownInFinally extends TestCase {

  public void tearDown() throws Exception {
    super.<warning descr="'tearDown()' not called from 'finally' block">tearDown</warning>();
    System.out.println();
  }
}
class NoProblem extends TestCase {

  public void tearDown() throws Exception {
    super.tearDown();
  }
}
class CalledInFinally extends TestCase {

  public void tearDown() throws Exception {
    try {
      System.out.println();
    } finally {
      super.tearDown();
    }
  }
}
class SomeTest extends TestCase {
  @Override
  protected void setUp() throws Exception {
    try {
      super.setUp();
    }
    catch (Throwable t) {
      super.tearDown(); // yellow code
    }
  }
  public void test_something() {}
}