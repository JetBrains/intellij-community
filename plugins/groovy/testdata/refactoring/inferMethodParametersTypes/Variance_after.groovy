def <T, U> void foo(ArrayList<T> a, ArrayList<?> b, ArrayList<U> c, U d) {
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

