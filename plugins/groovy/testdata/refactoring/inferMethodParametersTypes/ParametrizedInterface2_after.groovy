def <U0> Object foo(Comparable<U0> a, U0 b) {
  a.compareTo(b)
}

def m() {
  foo(1, 2)
  foo('s', 'q')
}
