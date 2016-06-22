interface I {
  void m();
}
interface J extends I {}

class Test {
  void foo(I i) {}

  {
    foo((J) (<caret>) -> {});
  }
}