class NotEnumMulti {

  public static void foo() {
    testMethod(new TestEnum[][]<caret>{{TestEnum.ONE}});
  }

  private static void testMethod(TestEnum[] values) { }

  public enum TestEnum {
    ONE, TWO, THREE
  }
}