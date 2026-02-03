class A<T> {

  List<T> x

    Object fo<caret>o(ArrayList<T> a, Closure<Void> b) {
    x = a
  }
}
new A<Integer>().foo([1]) {}