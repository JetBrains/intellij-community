def <V0, T1 extends V0> void foo(List<V0> x, T1[] a) {
  x.add(a[0])
}

foo([1], 2, 3)
foo(['s'], 'q', 'r')