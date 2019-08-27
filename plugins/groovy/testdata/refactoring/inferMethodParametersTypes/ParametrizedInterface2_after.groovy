def <T> int foo(Comparable<T> a, T b) {
  a.compareTo(b)
}

def m() {
  foo(1, 2)
  foo('s', 'q')
}
