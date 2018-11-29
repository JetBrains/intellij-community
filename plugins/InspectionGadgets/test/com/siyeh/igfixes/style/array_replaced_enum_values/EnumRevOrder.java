class EnumRevOrder {

  public static void foo() {
    testMethod(new TestEnum[]{TestEnum.THR<caret>EE, TestEnum.TWO, TestEnum.ONE});
  }

  private static void testMethod(TestEnum[] values) { }

  public enum TestEnum {
    ONE, TWO, THREE;
  }
}