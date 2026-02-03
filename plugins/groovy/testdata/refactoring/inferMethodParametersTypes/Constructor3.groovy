class A {
  I2 i2

  A<caret>(a) {
    i2 = a
  }
}

interface I1 {}

interface I2 {}

class B implements I1, I2 {}

class C implements I1, I2 {}

def m(B b, C c) {
  new A(b)
  new A(c)
}