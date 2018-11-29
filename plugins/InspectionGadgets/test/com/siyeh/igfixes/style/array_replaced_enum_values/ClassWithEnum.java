class ClassWithEnum {

  public static void foo() {
    testMethod(new TestEnum[]{TestEnum.ONE<caret>, TestEnum.TWO, TestEnum.THREE});
  }

  private static void testMethod(TestEnum[] values) { }

  public enum TestEnum {
    ONE, TWO, THREE
  }
}