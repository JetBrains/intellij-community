class SuperTearDownInFinally extends junit.framework.TestCase {

  public void tearDown() throws Exception {
    super.<warning descr="'tearDown()' not called from 'finally' block">tearDown</warning>();
    System.out.println();
  }
}
class NoProblem extends junit.framework.TestCase {

  public void tearDown() throws Exception {
    super.tearDown();
  }
}
class CalledInFinally extends junit.framework.TestCase {

  public void tearDown() throws Exception {
    try {
      System.out.println();
    } finally {
      super.tearDown();
    }
  }
}