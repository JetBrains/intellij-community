@com.intellij.testFramework.TestDataPath("$PROJECT_ROOT/projectSubdir<caret>")
class ATest extends LightCodeInsightFixtureTestCase {
  protected void doTest() {
    configureByFile(getTestName(true) + ".java");
  }
}