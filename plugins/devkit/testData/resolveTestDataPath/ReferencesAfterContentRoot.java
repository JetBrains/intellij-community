@com.intellij.testFramework.TestDataPath("$CONTENT_ROOT/contentRootSub<caret>dir")
class ATest extends LightCodeInsightFixtureTestCase {
  protected void doTest() {
    configureByFile(getTestName(true) + ".java");
  }
}