import static org.junit.Assert.assertEquals;

class OutsideTestMethod {

  void m() {
    <caret>assertEquals("asdf", null);
  }
}