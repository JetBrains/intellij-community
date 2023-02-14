@com.intellij.testFramework.TestDataPath("$CONTENT_ROOT")
@org.jetbrains.kotlin.test.TestMetadata("testData/refactoring")
public class ATest extends LightCodeInsightFixtureTestCase {

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

  @org.jetbrains.kotlin.test.TestMetadata("introduceVariable/SomeType.kt")
  public void testSomeType() throws Exception {
    runTest("testData/refactoring/introduceVariable/SomeType.kt");
  }

}
