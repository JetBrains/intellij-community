class ClassWithEnum {

  public static void foo() {
    testMethod(new TestEnum[]<caret>{TestEnum.ONE, TestEnum.TWO, TestEnum.THREE});
  }

  private static void testMethod(TestEnum[] values) { }

  public enum TestEnum {
    ONE, TWO, THREE
  }
}