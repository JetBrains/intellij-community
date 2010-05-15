class B extends A {
  def B() {
    super(27)
  }
}

class Usage {
  A a = new B();
}