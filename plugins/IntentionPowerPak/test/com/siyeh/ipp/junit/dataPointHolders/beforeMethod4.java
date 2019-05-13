// "Replace by @DataPoint field" "false"
class Foo {

  //wrong @DataPoint method declaration (returnType == void)
  @org.junit.experimental.the ories.DataPoint
  public static void ba<caret>r() {
  }

}
