def <W0, W1 extends W0> void foo(List<W0> x, W1[] a) {
  x.add(a[0])
}

foo([1], 2, 3)
foo(['s'], 'q', 'r')