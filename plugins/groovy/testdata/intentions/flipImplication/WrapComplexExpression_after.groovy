class X {
  def f() {
    def a = !(false || true) ==> !(true && false)
  }
}