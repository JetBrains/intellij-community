class RemoveParentheses {
  void test(int a, boolean b, int c, int d) {
    System.out.println(a + (b<caret> ? c + 1 : d + 2));
  }
}