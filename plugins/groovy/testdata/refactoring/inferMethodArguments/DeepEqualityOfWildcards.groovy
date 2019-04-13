def void foo(a, b) {
  a.add(b.get(0))
}

void  m () {
  foo([[2]], [[1]])
  foo([["q"]], [["s"]])
}