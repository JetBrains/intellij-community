import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
    assert<caret>True(false, "");
  }

  private String foo() {
    return null;
  }
}
