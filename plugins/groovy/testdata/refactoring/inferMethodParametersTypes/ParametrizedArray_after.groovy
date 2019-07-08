static <W0, W1 extends W0> void foo(List<W0> x, W1[] t) {
  x.add(t[0])
}

foo([1], [1, 2, 3] as Integer[])
foo(['q'], ['a', 'b', 'c'] as String[])
