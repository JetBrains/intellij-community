def <T0> Object foo(List<T0> a, T0 b) {
  a.add(b)
}

def m() {
  foo([], 1)
  foo([], 's')
}