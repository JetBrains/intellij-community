class G {
  int foo() {
    int <flown1>x = <caret>1;
    return x;
  }

  void test() {
    int <flown11111>f = <flown1111>bar();
  }

  int bar() {
    int <flown111>f = <flown11>foo();
    return f;
  }
}