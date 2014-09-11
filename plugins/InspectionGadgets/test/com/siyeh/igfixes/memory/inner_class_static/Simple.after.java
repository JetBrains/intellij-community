package memory.inner_class_static;

class Simple {
  static class Inner {}

  void m() {
    new Inner();
  }

  static void s(Simple s) {
    new Inner();
  }
}
class X {
  X() {
    new Simple.Inner();
  }
}
