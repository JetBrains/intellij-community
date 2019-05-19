import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

class EnumTest {
  enum A {foo, bar;}

  void foo() {
    Map<A, String> m = ((new <caret>EnumMap<>(A.class)));
  }
}