static <T> void foo(List<T> x, T[] t) {
  x.add(t[0])
}

foo([1], [1, 2, 3] as Integer[])
foo(['q'], ['a', 'b', 'c'] as String[])
