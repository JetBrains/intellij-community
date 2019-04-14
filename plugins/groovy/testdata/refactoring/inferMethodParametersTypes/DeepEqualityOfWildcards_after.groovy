def <T0 extends java.io.Serializable, U0 extends List<T0>, W0 extends List<U0>> void foo(List<U0> a, W0 b) {
  a.add(b.get(0))
}

void  m () {
  foo([[2]], [[1]])
  foo([["q"]], [["s"]])
}