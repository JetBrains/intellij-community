def <T> void foo(List<? extends List<T>> a, List<? extends List<? extends T>> b) {
  a.get(0).add(b.get(0).get(0))
}

class B{}
class A extends B{}

void  m () {
  foo([[1]], [[2]])
  foo([['s']], [["q"]])
  foo(new ArrayList<ArrayList<B>>(), new ArrayList<ArrayList<A>>())
}