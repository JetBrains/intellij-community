public class <warning descr="Test class 'TestCaseWithNoTestMethods' has no tests">TestCaseWithNoTestMethods</warning> extends junit.framework.TestCase {

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

abstract class AbstractTest extends junit.framework.TestCase {
  public void testInAbstract() {}
}

class MyImplTest extends AbstractTest {}
class MyImplImplTest extends MyImplTest {}

class NotATestClass {}

class MySuite {
  public static junit.framework.Test suite() {
    return null;
  }
}

class <warning descr="Test class 'TestCaseWithInner' has no tests">TestCaseWithInner</warning> extends junit.framework.TestCase {
  public static class Inner extends junit.framework.TestCase {
    public void test1() {}
  }
}

class Test5WithInner {
  static class Inner {
    @org.junit.jupiter.api.Test
    void test1() {}
  }
}

