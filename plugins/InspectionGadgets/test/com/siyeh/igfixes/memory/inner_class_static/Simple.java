package memory.inner_class_static;

class Simple {
  class Inner<caret> {}

  void m() {
    new Inner();
  }

  static void s(Simple s) {
    s.new Inner();
  }
}
class X {
  X() {
    new Simple().new Inner();
  }
}
