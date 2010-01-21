public class ATest extends LightCodeInsightFixtureTestCase {

  public void testTestNameAsParameter() throws Exception {
    doTest(getTestName(false));
  }

  private void doTest(String testName) throws Exception {
    configureByFile("before" + testName);
  }
}
