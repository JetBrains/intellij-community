class B<T> {

  class A {
    def f<caret>oo(a) {
      m(a)
    }
  }

  def m(T t) {
    foo(t)
  }
}

