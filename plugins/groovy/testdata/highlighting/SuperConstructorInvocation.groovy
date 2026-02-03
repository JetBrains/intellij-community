class A{
  def foo
}

class B extends A{
  def B() {
    super<warning descr="Cannot apply default constructor for class 'A'">(foo:'abc')</warning>
  }
}