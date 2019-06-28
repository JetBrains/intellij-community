import java.util.*;

class Test {

  volatile Map<String, String> map;

  void test() {
    map = Map.of<caret>("foo", "bar");
  }
}