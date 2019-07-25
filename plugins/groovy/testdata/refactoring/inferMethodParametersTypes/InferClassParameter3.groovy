class P<T> {
  def fo<caret>o(t) {
    m(t[0])
  }

  def m(T t) {
  }

}

def m(P<Integer> a, P<String> b) {
  a.foo([1])
  b.foo(['q'])
}
