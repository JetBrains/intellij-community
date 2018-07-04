class A<T> {
  void foo(A<T> a){}

  void bar(B b, B b1) {
      /*in method call*/
      /*comment in arg*/
      b1.foo(b);//end line comment
  }
}

class B extends A<String> {}