class A<T> {
  Q<T> x

  def fo<caret>o(a) {
    a.receive(x)
  }
}

class Q<T> {
  def receive(Q<T> ts) {

  }
}

new A<Integer>().foo(null as Q<Integer>)