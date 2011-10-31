interface I {
  def foo(int x<error descr="Default initializers are not allowed in abstract methods"> = 5</error>)
}

abstract class A {
  abstract foo(int x<error descr="Default initializers are not allowed in abstract methods"> = 5</error>)

  def abr(int x = 5){}
}
