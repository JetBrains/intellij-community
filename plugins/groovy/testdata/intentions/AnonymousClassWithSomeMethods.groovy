interface A {
  def a(int x, double y)

  def b(int x)
}
def x = new A<caret>(){
  def a(int x, double y) {}

  def b(int x) {}
}