def <U0> Object foo(List<? extends List<U0>> a, U0 b) {
  a.get(0).add(b)
}

class A{}
class B{}

def m(A a, B b) {
  foo([[a]], a)
  foo([[b]], b)
}
