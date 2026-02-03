def foo(l, a) {
  l.add(a)
}

def m() {
  foo(new ArrayList<>(), 1)
  foo(new ArrayList<>(), 's')
}