class Foo2 {
  void t2(String s) {
    boolean v = s.length//simple <caret>end comment
            () + 2 > 4 || s.isEmpty();
  }
}
