class Main {
  static class A{}
  static class B extends A{}
  void foo() {
    A a = new B()
    if (!(some<caret> || a instanceof B someName)) {}
  }
}