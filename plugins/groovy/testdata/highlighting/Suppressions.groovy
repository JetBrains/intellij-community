
class FooBarGoo {
  @SuppressWarnings(["GroovyUnresolvedAccess", "GroovyUntypedAccess"])
  def test1(def abc) {
    abc.def()
  }

  @SuppressWarnings("GroovyUnresolvedAccess")
  def test2(def abc) {
    <warning descr="Cannot determine type of 'abc'">abc</warning>.def()
  }

  def test3(def abc) {
    <warning descr="Cannot determine type of 'abc'">abc</warning>.def()
  }
}
