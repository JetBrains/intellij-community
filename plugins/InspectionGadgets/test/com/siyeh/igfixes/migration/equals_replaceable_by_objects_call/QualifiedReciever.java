class T {
  static class A {
    String s;
  }
  A a;
  static boolean same(T t, String s) {
    return t.a.s != null && t.a.s.<caret>equals(s);
  }
}