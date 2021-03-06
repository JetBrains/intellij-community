interface A {
  def foo(String a, Integer... integers)
}

class B implements A {
  void fo<caret>o(String x, Integer[] is) {}
}