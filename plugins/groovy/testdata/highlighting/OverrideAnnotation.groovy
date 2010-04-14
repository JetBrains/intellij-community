class Test {
  String str

  def foo(){}
}

class Foo extends Test{
  <warning descr="Method does not override method from its superclass">@Override</warning>
  def foo(String s){}
}