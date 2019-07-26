class A<T> {
  void fo<caret>o(List<T> a) {
    List<T> x = a
  }
}

new A<Integer>().foo([1])
