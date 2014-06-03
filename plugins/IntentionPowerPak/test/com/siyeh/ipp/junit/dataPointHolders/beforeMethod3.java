// "Replace by @DataPoint field" "false"
class Foo {

  //wrong @DataPoint method declaration (len(params) != 0)
  @org.junit.experimental.the ories.DataPoint
  public static int b<caret>ar(int j) {
    return 0;
  }

}
