class Bar {
  def call(int a) {}
}

class Foo {
  Bar getProp() {new Bar()}

  def foo() {
    prop<warning descr="'prop' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(2, 3)</warning>
    prop<warning descr="'prop' cannot be applied to '(java.lang.Integer)'">(2)</warning>
    prop<warning descr="'prop' cannot be applied to '()'">()</warning>
  }
}