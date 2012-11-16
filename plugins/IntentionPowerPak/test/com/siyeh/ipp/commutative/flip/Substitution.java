class A<T> {
  void foo(A<T> a){}

  void bar(B b, B b1) {
    b.f<caret>oo(b1);
  }
}

class B extends A<String> {}