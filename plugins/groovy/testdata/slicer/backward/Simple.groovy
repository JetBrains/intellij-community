class G {
  int foo() {
    int <flown111111>x = <flown1111111>1;
    return <flown11111>x;
  }

  void test() {
    int <caret>f = <flown1>bar();
  }

  int bar() {
    int <flown111>f = <flown1111>foo();
    return <flown11>f;
  }
}