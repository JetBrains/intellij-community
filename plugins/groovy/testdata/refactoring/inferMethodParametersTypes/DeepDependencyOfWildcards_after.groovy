def <T> void foo(ArrayList<? extends ArrayList<T>> a, ArrayList<? extends ArrayList<? extends T>> b) {
  a.get(0).add(b.get(0).get(0))
}

class B{}
class A extends B{}

void  m () {
  foo([[1]], [[2]])
  foo([['s']], [["q"]])
  foo(new ArrayList<ArrayList<B>>(), new ArrayList<ArrayList<A>>())
}