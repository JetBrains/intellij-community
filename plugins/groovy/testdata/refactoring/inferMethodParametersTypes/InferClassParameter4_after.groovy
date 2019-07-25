class P<T> {
  def foo(t) {
    m(t)
  }

  def m(List<T> t) {

  }
}

def m(P<Integer> a, P<String> b) {
  a.foo([1])
  b.foo(['q'])
}
