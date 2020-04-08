interface A {
  def foo(String a, Integer... integers)
}

class B implements A {
  def fo<caret>o(x, Integer... is) {}
}