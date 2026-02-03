class X {
  def f() {
    def a = true && false ==><caret> !((false || true))
  }
}