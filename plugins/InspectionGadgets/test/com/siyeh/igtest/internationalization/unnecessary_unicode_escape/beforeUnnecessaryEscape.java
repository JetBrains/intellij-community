// "Fix all 'Unnecessary unicode escape sequence' problems in file" "true"
class X {
  void test() {
    String s = "\u<caret>0061\u0062\u0063\u0064";
  }
}