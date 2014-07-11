import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

class OutsideTestMethod {

  void m() {
      assertEquals(null, "asdf");
  }
}