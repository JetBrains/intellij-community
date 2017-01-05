import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
      assertEquals(foo(), foo(), "");
  }

  private String foo() {
    return null;
  }
}
