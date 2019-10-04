class A {

  A<caret>(a, b) {
    a.compareTo(b)
    b.compareTo(a)
  }
}

def m() {
  new A(1, 1)
  new A('q', 'q')
}