class J {
  void test(G g, boolean b) {
    int <caret>f = b ? g.bar() : 123;
  }
}