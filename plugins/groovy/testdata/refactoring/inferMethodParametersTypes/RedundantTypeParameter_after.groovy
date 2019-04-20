def <T0 extends java.io.Serializable> Object foo(List<List<T0>> a, T0 b) {
  a.get(0).add(b)
}

foo([[1]], 2)
foo([['s']], 'q')