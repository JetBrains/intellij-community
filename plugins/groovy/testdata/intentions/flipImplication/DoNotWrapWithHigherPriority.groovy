class X {
  boolean foo() {
    return true
  }

  boolean bar() {
    return false
  }

  def f() {
    def c = foo() ==><caret> bar()
  }
}