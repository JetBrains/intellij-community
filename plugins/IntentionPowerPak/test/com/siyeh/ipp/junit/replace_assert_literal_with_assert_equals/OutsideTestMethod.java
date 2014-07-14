import static org.junit.Assert.assertNull;

class OutsideTestMethod {

  void m() {
    <caret>assertNull("asdf");
  }
}