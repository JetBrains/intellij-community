class J {
  int <flown111>foo() {
    int <flown1>x = <caret>1;
    return <flown11>x;
  }

  void test(G g) {
    int <flown1111111>f = <flown111111>g.bar(this);
  }
}