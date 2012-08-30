package org.jetbrains.android.refactoring;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.actions.InlineAction;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.android.AndroidTestCase;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidInlineLayoutTest extends AndroidTestCase {
  private static final String BASE_PATH = "refactoring/inlineLayout/";

  public void test1() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test2() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test3() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test4() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test5() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test6() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test7() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test8() throws Exception {
    doTestCommonInlineAll();
  }

  public void test9() throws Exception {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + "_included.xml", "res/layout/included.xml");
    myFixture.configureFromExistingVirtualFile(f);
    AndroidInlineLayoutHandler.setTestConfig(new AndroidInlineTestConfig(true));
    try {
      final Presentation p = myFixture.testAction(new InlineAction());
      assertTrue(p.isEnabled());
      assertTrue(p.isVisible());
    }
    finally {
      AndroidInlineLayoutHandler.setTestConfig(null);
    }
    myFixture.checkResultByFile("res/layout/test.xml", BASE_PATH + testName + "_after.xml", true);
    assertNull(myFixture.getTempDirFixture().getFile("res/layout/included.xml"));
  }

  public void test10() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test11() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test12() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test13() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test14() throws Exception {
    doTestCommonInlineThisOnly();
  }

  public void test15() throws Exception {
    myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "_included.xml", "res/layout-land/included.xml");
    doTestCommonInlineActionWithConflicts(false, true);
    myFixture.checkResultByFile("res/layout/test.xml", BASE_PATH + getTestName(true) + "_after.xml", true);
  }

  public void test16() throws Exception {
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCommonInlineActionWithConflicts(false, true);
    myFixture.checkResultByFile("res/layout/test.xml", BASE_PATH + getTestName(true) + "_after.xml", true);
  }

  public void test17() throws Exception {
    doTestCommonInlineAction(true);
  }

  public void test18() throws Exception {
    doTestCommonInlineAction(false);
  }

  public void test19() throws Exception {
    doTestInlineIncludeActionDisabled();
  }

  public void test20() throws Exception {
    doTestInlineIncludeActionError(true);
  }

  public void test21() throws Exception {
    doTestInlineIncludeAction(false);
  }

  public void test22() throws Exception {
    myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "_included.xml", "res/layout/included.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    AndroidInlineLayoutHandler.setTestConfig(new AndroidInlineTestConfig(true));
    try {
      myFixture.testAction(new InlineAction());
      fail();
    }
    catch (IncorrectOperationException e) {
      assertTrue(e.getMessage().length() > 0);
    }
    finally {
      AndroidInlineLayoutHandler.setTestConfig(null);
    }
  }

  private void doTestCommonInlineThisOnly() throws Exception {
    doTestCommonInlineAction(true);
    myFixture.checkResultByFile("res/layout/included.xml",
                                BASE_PATH + getTestName(true) + "_included.xml", true);
  }

  private void doTestCommonInlineAll() throws Exception {
    doTestCommonInlineAction(false);
    assertNull(myFixture.getTempDirFixture().getFile("res/layout/included.xml"));
  }

  private void doTestCommonInlineActionWithConflicts(boolean inlineThisOnly, boolean configureFromIncludedFile) {
    final String testName = getTestName(true);
    final VirtualFile included = myFixture.copyFileToProject(BASE_PATH + testName + "_included.xml", "res/layout/included.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(configureFromIncludedFile ? included : f);
    final AndroidInlineTestConfig config = new AndroidInlineTestConfig(inlineThisOnly);
    AndroidInlineLayoutHandler.setTestConfig(config);
    myFixture.testAction(new InlineAction());
    final MultiMap<PsiElement,String> conflicts = config.getConflicts();
    assertEquals(1, conflicts.keySet().size());
    assertEquals(1, conflicts.values().size());
  }

  private void doTestCommonInlineAction(boolean inlineThisOnly) {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_included.xml", "res/layout/included.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    AndroidInlineLayoutHandler.setTestConfig(new AndroidInlineTestConfig(inlineThisOnly));
    try {
      final Presentation p = myFixture.testAction(new InlineAction());
      assertTrue(p.isEnabled());
      assertTrue(p.isVisible());
    }
    finally {
      AndroidInlineLayoutHandler.setTestConfig(null);
    }
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml", true);
  }

  private void doTestInlineIncludeAction(boolean inlineThisOnly) {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_included.xml", "res/layout/included.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    final Presentation p = myFixture.testAction(new AndroidInlineIncludeAction(new AndroidInlineTestConfig(inlineThisOnly)));
    assertTrue(p.isEnabled());
    assertTrue(p.isVisible());
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml", true);
  }

  private void doTestInlineIncludeActionDisabled() {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_included.xml", "res/layout/included.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    final Presentation p = myFixture.testAction(new AndroidInlineIncludeAction(new AndroidInlineTestConfig(true)));
    assertTrue(p.isVisible());
    assertFalse(p.isEnabled());
  }

  private void doTestInlineIncludeActionError(boolean inlineThisOnly) {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_included.xml", "res/layout/included.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    try {
      myFixture.testAction(new AndroidInlineIncludeAction(new AndroidInlineTestConfig(inlineThisOnly)));
      fail();
    }
    catch (IncorrectOperationException e) {
      assertTrue(e.getMessage().length() > 0);
    }
  }
}
