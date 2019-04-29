def <T1, U1 extends T1> void foo(List<T1> a, List<T1> b, List<T1> c, List<U1> d) {
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

