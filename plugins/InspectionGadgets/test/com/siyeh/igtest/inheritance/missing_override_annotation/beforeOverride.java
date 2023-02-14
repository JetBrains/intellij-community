// "Annotate method 'test()' as '@Override'" "true"
class Super {
  void test() {}
}
class Child extends Super {
  void te<caret>st() {}
}