@com.intellij.testFramework.TestDataPath("$CONTENT_ROOT<caret>")
class ATest extends LightCodeInsightFixtureTestCase {
  protected void doTest() {
    configureByFile(getTestName(true) + ".java");
  }
}