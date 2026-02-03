class A {
  def foo(int x = 0) {}
}

def v = new A().&foo

v(0)
v()