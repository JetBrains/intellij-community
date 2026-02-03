class Main {
  static class A{}
  static class B extends A{}
  void foo() {
    A a = new B()
    def x = switch(a instanceof B someName) {
      case true -> {
        some<caret>
        yield 1
      }
      case false -> {
        yield 2
      }
    }
  }
}