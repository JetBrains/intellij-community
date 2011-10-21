public class Test {
  void foo(Object o) {
    if (o instanceof String) {
      Integer i = Integer.class.ca<caret>st(o);
    }
  }
}