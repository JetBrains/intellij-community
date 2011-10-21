public class Test {
  void foo(Object o) {
    if (o instanceof Integer) {
      Integer i = Integer.class.cast(o);
    }
  }
}