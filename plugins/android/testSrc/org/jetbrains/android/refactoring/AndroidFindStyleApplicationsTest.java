package org.jetbrains.android.refactoring;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFindStyleApplicationsTest extends AndroidTestCase {
  private static final String BASE_PATH = "refactoring/findPossibleUsages/";

  public void test1() throws Exception {
    doTest();
  }

  public void test2() throws Exception {
    doTest();
  }

  public void test3() throws Exception {
    doTest();
  }

  public void test4() throws Exception {
    doTest();
  }

  public void test5() throws Exception {
    doTest();
  }

  public void test6() throws Exception {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_layout.xml", "res/layout/layout1.xml");
    myFixture.copyFileToProject(BASE_PATH + testName + "_layout.xml", "res/layout/layout2.xml");

    doTest1();

    myFixture.checkResultByFile("res/layout/layout1.xml", BASE_PATH + testName + "_layout_after.xml", true);
    myFixture.checkResultByFile("res/layout/layout2.xml", BASE_PATH + testName + "_layout_after.xml", true);
  }

  public void test7() throws Exception {
    try {
      doTest();
      fail();
    }
    catch (RuntimeException e) {
      assertEquals("IDEA has not found any possible applications of style 'style1'", e.getMessage());
    }
  }

  private void doTest() {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_layout.xml", "res/layout/layout.xml");

    doTest1();

    myFixture.checkResultByFile("res/layout/layout.xml", BASE_PATH + testName + "_layout_after.xml", true);
  }

  private void doTest1() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/values/styles.xml");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.testAction(new AndroidFindStyleApplicationsAction(new AndroidFindStyleApplicationsAction.MyTestConfig(
      AndroidFindStyleApplicationsProcessor.MyScope.PROJECT)));
    myFixture.checkResultByFile(BASE_PATH + testName + ".xml");
  }
}
