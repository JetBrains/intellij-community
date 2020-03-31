def <T, T0> void foo(List<T> a, T b) {
  a.add(b)
}

foo([1], 1)
foo(['s'], 's')

