static void foo(x, t) {
  x.add(t[0])
}

foo([1], [1, 2, 3] as Integer[])
foo(['q'], ['a', 'b', 'c'] as String[])
