def <T> boolean foo(ArrayList<T> l, T a) {
  l.add(a)
}

def m() {
  foo(new ArrayList<>(), 1)
  foo(new ArrayList<>(), 's')
}