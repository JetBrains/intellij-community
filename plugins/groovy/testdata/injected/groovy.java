class MyClass {
  void evaluate(String s) {}
}

class A {
  void f(String groovy) {
    f("new MyClass().evaluate(\"dfdfg <caret>sdfdfd \")");
  }
}