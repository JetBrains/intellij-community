class B<T> {

  class A {
    Object f<caret>oo(T a) {
      m(a)
    }
  }

  def m(T t) {
    foo(t)
  }
}

