static <V0, T1 extends V0> void foo(List<V0> x, T1[] t) {
  x.add(t[0])
}

foo([1], [1, 2, 3] as Integer[])
foo(['q'], ['a', 'b', 'c'] as String[])
