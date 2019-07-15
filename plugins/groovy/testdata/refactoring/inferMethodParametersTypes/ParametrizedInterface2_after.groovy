def <V0> Object foo(Comparable<V0> a, V0 b) {
  a.compareTo(b)
}

def m() {
  foo(1, 2)
  foo('s', 'q')
}
