class Main {
  static class A{}
  static class B extends A{}
  void foo() {
    A a = new B()
    for(;a instanceof B someName;someName<caret>) {
    }
  }
}