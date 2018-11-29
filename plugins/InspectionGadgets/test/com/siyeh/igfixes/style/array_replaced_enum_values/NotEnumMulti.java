class NotEnumMulti {

  public static void foo() {
    testMethod(new TestEnum[][]{{TestEnum.ON<caret>E,"",""}});
  }

  private static void testMethod(TestEnum[] values) { }

  public enum TestEnum {
    ONE, TWO, THREE
  }
}