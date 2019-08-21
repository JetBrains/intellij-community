class A<T> {
  Q<T> x

    Object fo<caret>o(Q<T> a) {
    a.receive(x)
  }
}

class Q<T> {
  def receive(Q<T> ts) {

  }
}

new A<Integer>().foo(null as Q<Integer>)