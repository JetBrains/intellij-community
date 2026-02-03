void foo(x, ...a) {
  x.add(a[0])
}

foo([1], 2, 3)
foo(['s'], 'q', 'r')