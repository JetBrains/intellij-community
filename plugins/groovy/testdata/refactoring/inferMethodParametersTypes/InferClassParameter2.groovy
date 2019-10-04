class P<T> {
  def f<caret>oo(t) {
    m(t)
  }

  def m(T t) {

  }
}

def m(P<Integer> a, P<String> b) {
  a.foo(1)
  b.foo('q')
}
