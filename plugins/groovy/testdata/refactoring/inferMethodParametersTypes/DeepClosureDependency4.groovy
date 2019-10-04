def Object foo(a, c) {
  c([a], a)
}

class A{def x(){}}
class B{def x(){}}

def bar(A x, B y) {
  foo(x) {a, b -> a.add(b)}
  foo(y) {a, b -> a.add(b)}
}
