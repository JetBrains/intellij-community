@com.intellij.testFramework.TestDataPath("$PROJECT_ROOT<caret>")
class ATest extends LightCodeInsightFixtureTestCase {
  protected void doTest() {
    configureByFile(getTestName(true) + ".java");
  }
}