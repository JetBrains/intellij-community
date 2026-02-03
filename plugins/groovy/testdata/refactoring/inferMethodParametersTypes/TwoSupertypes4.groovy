def void foo(a, b, c, d) {
  a.add(b.get(0))
  c.add(d.get(0))
  b.add(d.get(0))
}

class A{}
class B{}

def m(A a, B b) {
  foo([a], [a], [a], [a])
  foo([b], [b], [b], [b])
}

