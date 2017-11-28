class J {
  int foo() {
    int x = <flown111111>1;
    return <flown11111>x;
  }

  void test(G g) {
    int <caret>f = <flown1>g.bar(this);
  }
}