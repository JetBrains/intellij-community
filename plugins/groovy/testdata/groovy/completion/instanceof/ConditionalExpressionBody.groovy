class Main {
  static class A{}
  static class B extends A{}
  void foo() {
    A a = new B()
    a instanceof B someName ? some<caret> : null
  }
}