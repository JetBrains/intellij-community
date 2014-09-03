public class <warning descr="JUnit test case 'TestCaseWithNoTestMethods' has no tests">TestCaseWithNoTestMethods</warning> extends junit.framework.TestCase {

  TestCaseWithNoTestMethods() {}

  public int testOne() {
    return 1;
  }

  public static void testTwo() {}
  void testThree() {}
  public void testFour(int i) {}

  public void setUp() throws Exception {
    super.setUp();
  }
  public void tearDown() throws Exception {
    super.tearDown();
  }
}