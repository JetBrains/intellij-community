class X {
  def f() {
    boolean a = false
    boolean b = true
    def c = !(a != b) ==> !(a == b)
  }
}