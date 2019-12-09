// "Replace 'if else' with '?:'" "true"
class PlusMinusTest {
  void foo(boolean b) {
    int x = 0;
    if<caret> (b) {
      x -= 1;
    } else {
      x += -2;
    }
  }
}