package org.jetbrains.android.refactoring;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidExtractAsIncludeTest extends AndroidTestCase {
  private static final String BASE_PATH = "refactoring/extractAsInclude/";

  public void test1() throws Exception {
    doTest();
  }

  public void test2() throws Exception {
    doTest();
  }

  public void test3() throws Exception {
    doTestDisabled();
  }

  public void test4() throws Exception {
    doTestDisabled();
  }

  public void test5() throws Exception {
    doTestDisabled();
  }

  public void test6() throws Exception {
    doTest();
  }

  public void test7() throws Exception {
    doTest();
  }

  public void test8() throws Exception {
    doTestDisabled();
  }

  public void test9() throws Exception {
    doTest();
  }

  public void test10() throws Exception {
    doTest();
  }

  private void doTest() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    final String extractedFileName = "extracted.xml";
    myFixture.testAction(new AndroidExtractAsIncludeAction(new AndroidExtractAsIncludeAction.MyTestConfig(extractedFileName)));
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml", true);
    myFixture.checkResultByFile("res/layout/" + extractedFileName, BASE_PATH + testName + "_extracted.xml", true);
  }

  private void doTestDisabled() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    final Presentation p =
      myFixture.testAction(new AndroidExtractAsIncludeAction(new AndroidExtractAsIncludeAction.MyTestConfig("extracted.xml")));
    assertTrue(p.isVisible());
    assertFalse(p.isEnabled());
  }
}
