class A {

  A<caret>(a, b) {
    a.compareTo(b)
    b.compareTo(a)
  }
}

class B{}
class C{}

def m(B b, C c) {
  new A(1, 1)
  new A('q', 'q')
}