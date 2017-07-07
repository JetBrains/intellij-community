class A {
  def field
  A(x) {}
}

new A()
new A(field: 1)
new A(field2: 1)

class B {
  def field
  B(int x = 0) {}
}

new B()

new B(field: 0)
new B(<warning descr="Property 'field1' does not exist">field1</warning>: 0)

class C {
  def field
  C(Map map){}
}

new C()
new C(field: 0)
new C(field1: 0)
