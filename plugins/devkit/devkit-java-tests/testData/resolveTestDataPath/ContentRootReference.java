@com.intellij.testFramework.TestDataPath("$C<caret>ONTENT_ROOT/path")
class ATest extends LightCodeInsightFixtureTestCase {
  protected void doTest() {
    configureByFile(getTestName(true) + ".java");
  }
}