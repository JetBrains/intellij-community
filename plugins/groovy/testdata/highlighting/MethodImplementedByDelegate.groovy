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

class <error descr="Method 'foo' is not implemented">C</error> implements I {
 //nothing here
}