def <T> boolean foo(List<T> a, T b) {
  a.add(b)
}

def m() {
  foo([], 1)
  foo([], 's')
}