
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class ParameterizedTestsDemo {
  enum E {
    A, B;
  }

  @ParameterizedTest
  @ValueSource(strings = {"A"})
  void testStrToEnum(E e) { }

  @ParameterizedTest
  @ValueSource(strings = {"1"})
  void testStrToPrimitive(int i) { }
}
