def <T0> Object foo(ArrayList<T0> l, T0 a) {
  l.add(a)
}

def m() {
  foo(new ArrayList<>(), 1)
  foo(new ArrayList<>(), 's')
}