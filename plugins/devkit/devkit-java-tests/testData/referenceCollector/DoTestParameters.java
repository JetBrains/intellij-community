public class ATest extends LightCodeInsightFixtureTestCase {

  public void testDoTestParameters() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest("java");
  }

  private void doTest(String extension) throws Exception {
    configureByFile(getTestName(true) + "." + extension);
  }

  private void configureByFile(@com.intellij.testFramework.TestDataFile String file) {
  }

  private String getTestName(boolean toUpperCase) {
    return null;
  }
}
