def Object foo(a, c) {
  c([a], a)
  a.x()
}

class A{def x(){}}

def bar(A x) {
  foo(x) {a, b ->}
}
