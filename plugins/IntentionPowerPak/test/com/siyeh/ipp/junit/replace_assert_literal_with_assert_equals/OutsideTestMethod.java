import static org.junit.Assert.assertTrue;

class OutsideTestMethod {

  void m() {
    <caret>assertTrue("asdf");
  }
}