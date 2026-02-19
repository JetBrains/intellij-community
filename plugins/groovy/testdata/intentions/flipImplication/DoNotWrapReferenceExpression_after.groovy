class X {
  static class Y {
    boolean flag = false

    boolean m() {
      return true
    }
  }

  def f() {
    def a = new Y()
    def b = new Y()
    def c = !b.m() ==> !a.flag
  }
}