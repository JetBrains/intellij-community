class Base {
  private def f = null
}

class A {
  def foo() {
    def var2 = <ref>f
  }
}