class RemoveParentheses {
  void test(int a, boolean b, int c, int d) {
      if (b) System.out.print<caret>ln(a + c + 1);
      else System.out.println(a + d + 2);
  }
}