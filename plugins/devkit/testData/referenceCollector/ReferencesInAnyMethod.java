public class ATest extends LightCodeInsightFixtureTestCase {

  public void testReferencesInAnyMethod() throws Exception {
    resolve();
  }

  private void resolve() throws Exception {
    configureByFile("before");
  }

  private void configureByFile(@com.intellij.testFramework.TestDataFile String file) {
  }
}
