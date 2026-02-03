class A {
  def foo() {
    def x = new A() {
      def foo() {}
    }
  }
}

class B extends A {
  def foo() {}
}