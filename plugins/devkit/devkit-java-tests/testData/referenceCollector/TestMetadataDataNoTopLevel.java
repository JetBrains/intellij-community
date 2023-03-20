public class ATest extends LightCodeInsightFixtureTestCase {

  @com.intellij.testFramework.TestDataPath("$CONTENT_ROOT")
  @org.jetbrains.kotlin.test.TestMetadata("testData/refactoring/introduceVariable")
  public static class IntroduceVariable extends ATest {
    private void runTest(String testDataFilePath) throws Exception {

    }

    @org.jetbrains.kotlin.test.TestMetadata("AnonymousType.kt")
    public void testAnonymousType() throws Exception {
      runTest("testData/refactoring/introduceVariable/AnonymousType.kt");
    }

    @org.jetbrains.kotlin.test.TestMetadata("testData/refactoring/introduceVariable/extra")
    public static class IntroduceExtraVariable extends ATest {
      private void runTest(String testDataFilePath) throws Exception {

      }

      @org.jetbrains.kotlin.test.TestMetadata("AnonymousType.kt")
      public void testAnonymousType() throws Exception {
        runTest("testData/refactoring/introduceVariable/extra/AnonymousType.kt");
      }
    }
  }

}
