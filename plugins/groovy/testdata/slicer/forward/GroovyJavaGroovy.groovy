class G {
  int foo() {
    int <flown1>x = <caret>1;
    return x;
  }

  void test(J j) {
    int <flown1111111>f = <flown111111>j.bar(this);
  }
}