def <T0, X0> void foo(List<T0> a, List<?> b, List<X0> c, X0 d) {
  // invariant type variable
  a.add(a.get(0))
  // covariant type variable
  def x = b.get(0)
  // contravariant type variable
  c.add(d)
}

class A {}
class B {}

def m(A a, B b) {
  foo([a], [a], [a], a)
  foo([b], [b], [b], b)
}

