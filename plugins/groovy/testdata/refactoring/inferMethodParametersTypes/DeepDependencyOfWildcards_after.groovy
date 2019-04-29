def <W0, X0 extends W0> void foo(List<? extends List<W0>> a, List<? extends List<X0>> b) {
  a.get(0).add(b.get(0).get(0))
}

class B{}
class A extends B{}

void  m () {
  foo([[1]], [[2]])
  foo([['s']], [["q"]])
  foo(new ArrayList<ArrayList<B>>(), new ArrayList<ArrayList<A>>())
}