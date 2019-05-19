import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
      assert<caret>Equals(foo(), "const", "");
  }

  private String foo() {
    return null;
  }
}
