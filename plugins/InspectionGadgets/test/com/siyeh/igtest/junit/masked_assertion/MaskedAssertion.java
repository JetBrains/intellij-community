import java.io.ByteArrayInputStream;
import java.io.IOException;
import junit.framework.AssertionFailedError;
import static org.junit.Assert.*;

class Test {

  public void javaAssertionPositive1() {
    try {
      <warning descr="'assert' cannot fail as it's suppressed by a surrounding 'catch'">assert 1 == 1;</warning>
    } catch (AssertionError e) {}
  }

  public void javaAssertionPositive2() {
    try {
      int a = 1;
      <warning descr="'assert' cannot fail as it's suppressed by a surrounding 'catch'">assert 1 == 1;</warning>
    } catch (AssertionError e) {}
  }

  public void javaAssertionPositive3() {
    try {
      <warning descr="'assert' cannot fail as it's suppressed by a surrounding 'catch'">assert 1 == 1;</warning>
    } catch (Error e) {}
  }

  public void javaAssertionPositive4() {
    try {
      <warning descr="'assert' cannot fail as it's suppressed by a surrounding 'catch'">assert 1 == 1;</warning>
    } catch (Throwable e) {}
  }

  public void javaAssertionPositive5() {
    try {
      <warning descr="'assert' cannot fail as it's suppressed by a surrounding 'catch'">assert 1 == 1;</warning>
    } catch (AssertionFailedError e) {
    } catch (AssertionError e) {
    }
  }

  public void javaAssertionNegative1() {
    try {
      assert 1 == 1;
    } catch (AssertionError e) {
      System.out.println();
    }
  }

  public void javaAssertionNegative2() {
    try {
      assert 1 == 1;
      int a = 1;
    } catch (AssertionError e) {
    }
  }

  public void javaAssertionNegative3() {
    try {
      assert 1 == 1;
    } catch (Exception e) {
    }
  }

  public void assertSamePositive1() {
    try {
      <warning descr="'assertSame()' cannot fail as it's suppressed by a surrounding 'catch'">assertSame(1, 1)</warning>;
    } catch (AssertionError e) {}
  }

  public void assertSamePositive2() {
    try {
      int a = 1;
      <warning descr="'assertSame()' cannot fail as it's suppressed by a surrounding 'catch'">assertSame(1, 1)</warning>;
    } catch (AssertionError e) {}
  }

  public void assertSameNegative1() {
    try {
      assertSame(1, 1);
    } catch (AssertionError e) {
      System.out.println();
    }
  }

  public void assertSameNegative2() {
    try {
      assertSame(1, 1);
      int a = 1;
    } catch (AssertionError e) {
    }
  }

  public void assertSameNegative3() throws IOException {
    try(ByteArrayInputStream bis = new ByteArrayInputStream(new byte[8192])) {
      assertSame(1, 1);
    }
  }

  public void failPositive1() {
    try {
      <warning descr="'fail()' cannot fail as it's suppressed by a surrounding 'catch'">fail()</warning>;
    } catch (AssertionError e) {}
  }

  public void failPositive2() {
    try {
      int a = 1;
      <warning descr="'fail()' cannot fail as it's suppressed by a surrounding 'catch'">fail()</warning>;
    } catch (AssertionError e) {}
  }

  public void failPositive3() {
    try {
      <warning descr="'fail()' cannot fail as it's suppressed by a surrounding 'catch'">fail()</warning>;
    } catch (Exception e1) {
    } catch (AssertionError e2) {}
  }

  public void failPositive4() {
    try {
      <warning descr="'fail()' cannot fail as it's suppressed by a surrounding 'catch'">fail()</warning>;
    } catch (AssertionFailedError e) {
    } catch (AssertionError e) {
    }
  }

  public void failPositive5() {
    try {
      <warning descr="'fail()' cannot fail as it's suppressed by a surrounding 'catch'">fail()</warning>;
    } catch (NullPointerException | AssertionError e) {
    } catch (Throwable e) {
      System.out.println();
    }
  }

  public void failNegative1() {
    try {
      fail();
    } catch (AssertionError e) {
      System.out.println();
    }
  }

  public void failNegative2() {
    try {
      fail();
      int a = 1;
    } catch (AssertionError e) {
    }
  }

  public void failNegative3() {
    try {
      fail();
    } finally {
    }
  }

  public void failNegative4() {
    try {
      fail();
    } catch (AssertionFailedError e) {
      System.out.println();
    } catch (AssertionError e) {
    }
  }
}