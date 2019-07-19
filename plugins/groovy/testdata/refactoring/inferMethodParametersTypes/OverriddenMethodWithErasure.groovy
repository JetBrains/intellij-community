interface A {
  def <T> void foo(Map a, T s)
  def <T> void foo(T s)
}

class B implements A {

  def <T> void fo<caret>o(a = [:], T s) {
  }
}