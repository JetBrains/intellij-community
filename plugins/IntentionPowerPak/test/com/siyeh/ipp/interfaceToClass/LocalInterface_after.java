class A {
  void foo() {
    abstract class Lo<caret>cal {}
    class X extends Local {}
    class X1 extends Local implements Cloneable {}
  }
}