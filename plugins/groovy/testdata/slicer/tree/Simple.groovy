class G {
  int foo() {
    int x = 1234567;
    return x;
  }

  void test() {
    int <caret>f = bar();
  }

  int bar() {
    int f = foo();
    return f;
  }
}