class C {
  def clos = {de<caret>f p -> print p}

  def foo() {
    clos(1)
    clos.call(2)

    getClos()(3)
    getClos().call(4)
  }
}
def c = new C()
c.clos(5)
c.clos.call(6)

c.getClos()(7)
c.getClos().call(8)
