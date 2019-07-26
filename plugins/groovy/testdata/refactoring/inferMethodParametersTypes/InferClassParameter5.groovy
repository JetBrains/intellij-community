class A<T> {
  def fo<caret>o(a) {
    List<T> x = a
  }
}

new A<Integer>().foo([1])
