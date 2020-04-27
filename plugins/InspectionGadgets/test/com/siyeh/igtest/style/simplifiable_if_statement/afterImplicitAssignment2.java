// "Replace 'if else' with '?:'" "INFORMATION"
class X {
  void test(int f) {
    String s = "none";
    System.out.println(s);
      s = f > 0 ? "false" : "true";
  }
}