def Object foo(a, b) {
  a.get(0).get(0).add(b)
}

foo([[[1]]], 2)
foo([[['s']]], 'q')