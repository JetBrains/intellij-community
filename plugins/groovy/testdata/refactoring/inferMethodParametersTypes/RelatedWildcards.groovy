void foo(a, b) {
  a.add(b.get(0))
}


class A{}
class B{}

def m(A a, B b) {
  foo([a], [a])
  foo([b], [b])
}
