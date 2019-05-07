import java.util.*;

class Test {

  void test(boolean b) {
    Map.Entry<String, String> oneEntry = Map.entry("foo", "baz");
    Map.Entry<String, String> anotherEntry = Map.entry("baz", "qux");
      Map<String, String> x = new HashMap<>();
      x.put((b ? oneEntry : anotherEntry).getKey(), (b ? oneEntry : anotherEntry).getValue());
      System.out.println(x);
  }
}