class Test {
  def <T> void f(List<? extends T> l)  {
    new A().te<caret>st(l)
  }
}
