class Bar {
  def call(int a) {}
}

class Foo {
  Bar getProp() {new Bar()}

  def foo() {
    prop<warning descr="'call' in 'Bar' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(2, 3)</warning>
    prop(2)
    prop<warning descr="'call' in 'Bar' cannot be applied to '()'">()</warning>
  }
}