def foo(a, b) {
  a.get(0).add(b)
}

class A{}
class B{}

def m(A a, B b) {
  foo([[a]], a)
  foo([[b]], b)
}
