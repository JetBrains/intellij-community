class P<T> {
  Object foo(List<? extends T> t) {
    m(t[0])
  }

  def m(T t) {
  }

}

def m(P<Integer> a, P<String> b) {
  a.foo([1])
  b.foo(['q'])
}
