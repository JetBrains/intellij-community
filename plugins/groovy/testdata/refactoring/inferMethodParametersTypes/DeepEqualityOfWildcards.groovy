def foo(a, b) {
  a.add(b.get(0))
}

void  m () {
  foo([[1]], [[2]])
  foo([['s']], [["q"]])
}