class A {
  A(a) {}
  A(int a) {}
  static 'new'(a, b, c) {}
}

def a = A::new
a()
a(1)
a("")
a<warning descr="'a' cannot be applied to '(null, null)'">(null, null)</warning>
a(1, 2, 3)
