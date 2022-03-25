static def foo(a, b) {
  b.add(a)
}

static def bar() {
  foo(1, [1])
  foo("", [""])
}