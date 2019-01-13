class ErrorInMultiDArray {

  public static void foo() {
    testMethod(new TestEnum[]{{TestE<caret>num.ONE, TestEnum.TWO, TestEnum.THREE}});
  }

  private static void testMethod(TestEnum[] values) { }

  public enum TestEnum {
    ONE, TWO, THREE;
}