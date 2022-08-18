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

<error descr="Class 'TestCaseWithInner' is public, should be declared in a file named 'TestCaseWithInner.java'">public class <warning descr="Test class 'TestCaseWithInner' has no tests">TestCaseWithInner</warning> extends junit.framework.TestCase</error> {
  public static class Inner extends junit.framework.TestCase {
    public void test1() {}
  }
}

class Test5WithInner {
  @org.junit.jupiter.api.Nested
  class Inner {
    @org.junit.jupiter.api.Test
    void test1() {}
  }
}

class <warning descr="Test class 'Test5WithInner1' has no tests">Test5WithInner1</warning> {
  @org.junit.jupiter.api.Nested
  class <warning descr="Test class 'Inner' has no tests">Inner</warning> {
    void test1() {}
  }
}
@org.junit.Ignore
class IgnoredTest extends junit.framework.TestCase {}