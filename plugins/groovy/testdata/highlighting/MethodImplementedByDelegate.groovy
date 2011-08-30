interface I {
  def foo()
}

class A implements I {
  def foo(){}
}

class B implements  I{
  @Delegate
  def A a
}

<error descr="Method 'foo' is not implemented">class C implements I</error> {
 //nothing here
}