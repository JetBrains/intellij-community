class Test {
  @org.jetbrains.annotations.NonNls
  private Object[][] testNonNls() {
    return new Object[][]{
      {1, 2, (new String[][]{{"aaa"}})}
    };
  }

  private Object[][] testNls() {
    return new Object[][]{
      {1, 2, (new String[][]{{<warning descr="Hardcoded string literal: \"aaa\"">"aaa"</warning>}})}
    };
  }
}