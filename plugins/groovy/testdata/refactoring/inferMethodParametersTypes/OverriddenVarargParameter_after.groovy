interface A {
  def foo(String a, Integer... integers)
}

class B implements A {
  Object fo<caret>o(String x, Integer[] is) {}
}