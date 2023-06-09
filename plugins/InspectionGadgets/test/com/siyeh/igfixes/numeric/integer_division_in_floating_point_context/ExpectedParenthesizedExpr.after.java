class ExpectedParenthesizedExpr {

  public void test(Integer i) {
    double x = 1 + (double) (i * 2)<caret> / 5;
    System.out.println(x);
  }
}