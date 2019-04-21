def void foo(a, b, c, d) {
  a.add(b.get(0))
  c.add(d.get(0))
  b.add(d.get(0))
}

foo([1], [2], [3], [4])
foo(['a'], ['b'], ['c'], ['d'])