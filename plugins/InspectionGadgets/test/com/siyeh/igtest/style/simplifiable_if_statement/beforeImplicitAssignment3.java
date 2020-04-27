// "Replace 'if else' with '?:'" "false"
class X {
  void test(int f) {
    String s = "none";
    System.out.println(s);
    s += "true";
    <caret>if (f > 0) s += "false";
  }
}