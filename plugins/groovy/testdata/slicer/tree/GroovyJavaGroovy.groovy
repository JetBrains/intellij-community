class G {
  int foo() {
    int x = 456;
    return x;
  }

  void test(J j) {
    int <caret>f = j.bar(this);
  }
}