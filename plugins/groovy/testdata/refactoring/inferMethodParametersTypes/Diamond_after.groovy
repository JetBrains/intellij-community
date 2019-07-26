def <U0> Object foo(ArrayList<U0> l, U0 a) {
  l.add(a)
}

def m() {
  foo(new ArrayList<>(), 1)
  foo(new ArrayList<>(), 's')
}