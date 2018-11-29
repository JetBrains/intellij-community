class NotEnumInit {

  public static void foo() {
    testMethod(new TestEnum[]{"", "", TestEnum.O<caret>NE});
  }

  private static void testMethod(TestEnum[] values) { }

  public enum TestEnum {
    ONE, TWO, THREE
  }
}