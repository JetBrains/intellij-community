def foo(a, b) {
  bar(a, b)
}

class A {

}

class B {

}

def <T> void bar(T a, T b) {

}
A a = new A()
B b = new B()
foo(a, a)
foo(b, b)