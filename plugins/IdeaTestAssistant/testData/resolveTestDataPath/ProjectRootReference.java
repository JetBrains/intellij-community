@com.intellij.testFramework.TestDataPath("$P<caret>ROJECT_ROOT/path")
class ATest extends LightCodeInsightFixtureTestCase {
  protected void doTest() {
    configureByFile(getTestName(true) + ".java");
  }
}