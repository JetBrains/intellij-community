public class ATest extends LightCodeInsightFixtureTestCase {

  public void testFixtureConfigureByFile() throws Exception {
    doFileTest("before", "after");
  }

  private void doFileTest(@com.intellij.testFramework.TestDataFile String before,
                          @com.intellij.testFramework.TestDataFile String after) {
  }
}
