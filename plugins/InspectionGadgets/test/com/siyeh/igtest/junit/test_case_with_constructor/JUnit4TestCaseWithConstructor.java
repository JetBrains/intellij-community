public class JUnit4TestCaseWithConstructor {

  public <warning descr="Initialization logic in constructor 'JUnit4TestCaseWithConstructor()' instead of 'setUp()'">JUnit4TestCaseWithConstructor</warning>() {
    System.out.println();
    System.out.println();
    System.out.println();
  }

  @org.junit.Test
  public void testMe() {}
}