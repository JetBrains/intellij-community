class A<T> {
  Q<T> x

  def f<caret>oo(a) {
    a.receive(x)
  }
}

class Q<T> {
  def receive(Q<? extends T> ts) {

  }
}

new A<Integer>().foo(null as Q<Integer>)