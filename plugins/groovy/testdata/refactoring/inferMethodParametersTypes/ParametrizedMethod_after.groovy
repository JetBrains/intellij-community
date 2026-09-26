Object foo(A a, B b) {
  a.tie(b)
}


class A{
  def <T> void tie(T t) {}
}

class B {}


def m(A a, B b) {
  foo(a, b)
}