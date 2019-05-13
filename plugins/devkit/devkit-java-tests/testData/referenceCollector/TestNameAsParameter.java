public class ATest extends LightCodeInsightFixtureTestCase {

  public void testTestNameAsParameter() throws Exception {
    doTest(getTestName(false));
  }

  private void doTest(String testName) throws Exception {
    configureByFile("before" + testName);
  }

  private void configureByFile(@com.intellij.testFramework.TestDataFile String file) {
  }

  private String getTestName(boolean toUpperCase) {
    return null;
  }
}
