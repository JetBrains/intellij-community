interface A {
  def a()
}
def x = new A<caret>() {
  def a() {
    print "wow"
  }
}