def <T0> void foo(List<T0> a, T0 x) {
  a.add(x)
}

foo([1, 2, 3], 4)

foo(["a", "b", "c"], "d")
