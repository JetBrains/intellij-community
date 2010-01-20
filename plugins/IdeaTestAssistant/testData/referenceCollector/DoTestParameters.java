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
}
