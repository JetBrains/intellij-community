class InnerEnum {

  public static void foo() {
    testMethod(Inner.values());
  }

  private static void testMethod(TestEnum.Inner[] values) {
  }

  public enum TestEnum {
    ONE, TWO, THREE;

    enum Inner {FIVE, SIX, SEVEN}
  }
}