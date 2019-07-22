def Object foo(a, c) {
  c([a], a)
}

class A{}

def bar(A x) {
  foo(x) {a, b -> a.add(b)}
}
