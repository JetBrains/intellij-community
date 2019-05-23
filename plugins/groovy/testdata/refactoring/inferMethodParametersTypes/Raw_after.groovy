def <U0> Object foo(List<U0> a, U0 b) {
  a.add(b)
}

def m() {
  foo([], 1)
  foo([], 's')
}