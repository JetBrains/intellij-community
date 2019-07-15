def <X0, W1 extends X0> void foo(List<X0> x, W1[] a) {
  x.add(a[0])
}

foo([1], 2, 3)
foo(['s'], 'q', 'r')