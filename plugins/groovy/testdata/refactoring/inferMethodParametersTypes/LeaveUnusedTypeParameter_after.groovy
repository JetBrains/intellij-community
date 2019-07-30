def <T0, W0> void foo(List<W0> a, W0 b) {
  a.add(b)
}

foo([1], 1)
foo(['s'], 's')

