class A<T> {

  List<T> x

  Object fo<caret>o(a) {
    x = a
  }
}
new A<Integer>().foo([1])