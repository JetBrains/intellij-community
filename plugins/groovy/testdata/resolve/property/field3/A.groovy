class Base {
  private def f = null
}

class A extends Base {
  def foo() {
    def var2 = <ref>f
  }
}