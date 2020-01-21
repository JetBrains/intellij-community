// "Replace 'if else' with '?:'" "INFORMATION"
class X {
  void test(int f) {
    String s;
    s = "true";
    <caret>if (f > 0) s = "false";
  }
}