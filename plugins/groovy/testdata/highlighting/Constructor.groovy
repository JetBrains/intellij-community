class A {
  def field

  def A(x) {}

  def test() {
    new A<warning descr="Constructor 'A' in 'A' cannot be applied to '()'">()</warning>

    new A(field: 1)
    new A(field2: 1)
  }
}

class B {
  def field

  def A(int x = 0) {}

  def test() {
    new B()

    new B(field: 0)
    new B(<warning descr="Property 'field1' does not exist">field1</warning>: 0)
  }
}

class C {
  def field

  def C(Map map){}

  def test() {
    new C(field: 0)
    new C(field1: 0)
    new C<warning descr="Constructor 'C' in 'C' cannot be applied to '()'">()</warning>
  }
}