class LocalClass {

  void foo() {
    class A<T> {
      void foo() {}
    }
    class B<T> extends A<T> {}
    A<String> bb = new B<>();
    bb.foo();
  }
}