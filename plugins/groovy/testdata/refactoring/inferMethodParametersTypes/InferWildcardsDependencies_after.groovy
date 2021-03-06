def <T> void foo(ArrayList<T> a, T x) {
  a.add(x)
}

class A{}
class B{}

def m(A a, B b) {
  foo([a], a)

  foo([b], b)
}
