class A<T> {
  void foo(A<T> a){}

  void bar(B b, B b1) {
      b1.foo(b);
  }
}

class B extends A<String> {}