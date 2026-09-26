def foo(a) {
  I1 _ = a
}

interface I1{}
interface I2{}

class A implements I1, I2{}
class B implements I1, I2{}

def m(A a, B b) {
  foo(a)
  foo(b)
}