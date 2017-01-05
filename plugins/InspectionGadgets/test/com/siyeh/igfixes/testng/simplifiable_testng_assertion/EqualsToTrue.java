import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
    assert<caret>Equals(foo(), true, "");
  }

  private boolean foo() {
    return false;
  }
}
