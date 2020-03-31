import java.util.*;

class Test {
  class A {
  }

  class B extends A {
    Map<String, String> map = new HashMap<>();
  }

  class C extends A {
    private int cnt = 0;

    B getB() {
      cnt++;
      return null;
    }
  }

  void test(A a) {
      Map<String, String> stringStringMap = new HashMap<>();
      stringStringMap.put("foo", "bar");
      ((a instanceof C) ? ((C) a).getB() : ((B) a)).map = stringStringMap;
  }
}