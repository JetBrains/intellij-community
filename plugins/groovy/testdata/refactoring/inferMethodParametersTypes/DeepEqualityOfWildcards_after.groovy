def <V0 extends java.io.Serializable, W0 extends java.util.List<V0>> Object foo(List<List<V0>> a, List<W0> b) {
  a.add(b.get(0))
}

void  m () {
  foo([[1]], [[2]])
  foo([['s']], [["q"]])
}