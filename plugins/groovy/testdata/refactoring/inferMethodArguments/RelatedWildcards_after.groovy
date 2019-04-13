def <T0 extends java.io.Serializable, U0 extends T0> void foo(List<T0> a, List<U0> b) {
  a.add(b.get(0))
}

void  m () {
  foo([2], [1])
  foo(["q"], ["s"])
}