def <T> boolean foo(ArrayList<T> a, T b) {
  a.add(b)
}

def m() {
  foo([], 1)
  foo([], 's')
}