public class Test {
  void foo(Object o) {
    if (o instanceof String) {
      Integer i = String.class.cast(o);
    }
  }
}