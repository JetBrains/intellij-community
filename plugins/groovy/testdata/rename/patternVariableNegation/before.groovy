class X {
  static class A {}
  static class B extends A{}

  def m() {
    A a = new B()
    if (!(a instanceof B b<caret>)) {
    } else {
      println b.toString()
    }
  }
}