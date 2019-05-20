def <T0> void foo(List<? extends List<T0>> a, List<? extends List<? extends T0>> b) {
  a.get(0).add(b.get(0).get(0))
}

class B{}
class A extends B{}

void  m () {
  foo([[1]], [[2]])
  foo([['s']], [["q"]])
  foo(new ArrayList<ArrayList<B>>(), new ArrayList<ArrayList<A>>())
}