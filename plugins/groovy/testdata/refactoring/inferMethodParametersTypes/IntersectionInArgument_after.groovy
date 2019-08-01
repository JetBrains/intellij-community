def <U0 extends X2 & X1> Object foo(X3 a, U0 c) {
  a.h(c)
  a.i(c)
}

interface X1 {}
interface X2 {}
interface X3 {void h(X1 x1); void i(X2 x2)}

class C implements X1, X2 {}
class D implements X1, X2 {}

def m(X3 a, C c, D d) {
  foo(a, c)
  foo(a, d)
}