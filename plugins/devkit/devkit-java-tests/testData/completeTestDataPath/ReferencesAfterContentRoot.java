@com.intellij.testFramework.TestDataPath("$CONTENT_ROOT/c<caret>")
class ATest extends LightCodeInsightFixtureTestCase {
  protected void doTest() {
    configureByFile(getTestName(true) + ".java");
  }
}