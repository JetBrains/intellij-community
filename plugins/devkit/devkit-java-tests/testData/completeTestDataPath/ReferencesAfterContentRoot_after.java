@com.intellij.testFramework.TestDataPath("$CONTENT_ROOT/contentRootSubdir<caret>")
class ATest extends LightCodeInsightFixtureTestCase {
  protected void doTest() {
    configureByFile(getTestName(true) + ".java");
  }
}