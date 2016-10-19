import static org.testng.Assert.*;

class MyTestSimplifaibleAssertions {
  public void testFoo() throws Exception {
      assertNotNull(foo());
  }

  private String foo() {
    return null;
  }
}
