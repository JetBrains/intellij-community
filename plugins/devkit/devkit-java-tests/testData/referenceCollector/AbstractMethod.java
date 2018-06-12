abstract class AbstractClass extends LightCodeInsightFixtureTestCase {
  public void assertResolvesTo() {
    doTest();
  }

  protected abstract void doTest();
}

class ATest extends AbstractClass {
  public void testAbstractMethod() {
    assertResolvesTo();
  }

  protected void doTest() {
    configureByFile(getTestName(true) + ".java");
  }

  private void configureByFile(@com.intellij.testFramework.TestDataFile String file) {
  }

  private String getTestName(boolean toUpperCase) {
    return null;
  }
}