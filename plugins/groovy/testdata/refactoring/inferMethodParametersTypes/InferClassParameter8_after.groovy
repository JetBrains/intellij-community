class A<T> {
  Q<T> x

    Object f<caret>oo(Q<? super T> a) {
    a.receive(x)
  }
}

class Q<T> {
  def receive(Q<? extends T> ts) {

  }
}

new A<Integer>().foo(null as Q<Integer>)