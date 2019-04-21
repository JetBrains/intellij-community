def <T0 extends V0, U0 extends T0, V0 extends java.io.Serializable, W0 extends U0> void foo(List<T0> a, List<U0> b, List<V0> c, List<W0> d) {
  a.add(b.get(0))
  c.add(d.get(0))
  b.add(d.get(0))
}

foo([1], [2], [3], [4])
foo(['a'], ['b'], ['c'], ['d'])