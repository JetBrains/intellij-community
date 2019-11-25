// "Replace 'if else' with '?:'" "true"
class PlusMinusTest {
  void foo(int a, int b) {
    int c = 0;
      c += a < b ? a - b : -a * b;
  }
}