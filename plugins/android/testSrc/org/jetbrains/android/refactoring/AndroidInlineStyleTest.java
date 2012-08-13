package org.jetbrains.android.refactoring;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.actions.InlineAction;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidInlineStyleTest extends AndroidTestCase {
  private static final String BASE_PATH = "refactoring/inlineStyle/";

  public void test1() {
    doTest(true);
  }

  public void test2() {
    doTest(true);
  }

  public void test3() {
    doTest(true);
  }

  public void test4() {
    doTest(true);
  }

  public void test5() {
    doTestDisabled();
  }

  public void test6() {
    doTest(true);
  }

  public void test7() {
    doTest(false);
  }

  public void test8() {
    doTestDisabled();
  }

  public void test9() {
    doTestErrorMessageShown(true, true, false);
  }

  public void test10() {
    doTestDisabled();
  }

  public void test11() {
    doTestErrorMessageShown(true, true, false);
  }

  public void test12() {
    doTestErrorMessageShown(true, true, true);
  }

  public void test13() {
    doTestErrorMessageShown(true, true, true);
  }

  public void test14() {
    final String libStylesPath = getContentRootPath("lib") + "/res/values/styles.xml";
    final String stylesLocalPath = BASE_PATH + getTestName(true) + "_styles.xml";
    myFixture.copyFileToProject(stylesLocalPath, libStylesPath);
    doTestErrorMessageShown(true, true, true);
    myFixture.checkResultByFile(libStylesPath, stylesLocalPath, true);
  }

  public void test15() {
    final String libModuleDir = getContentRootPath("lib");
    final String libStylesPath = libModuleDir + "/res/values/styles.xml";
    final String appStylePath = "/res/values/styles.xml";
    final String appLayoutLocalPath = BASE_PATH + getTestName(true) + "_1.xml";
    final String stylesLocalPath = BASE_PATH + getTestName(true) + "_styles.xml";
    final String appLayoutPath = "res/layout/layout.xml";

    myFixture.copyFileToProject(stylesLocalPath, libStylesPath);
    myFixture.copyFileToProject(stylesLocalPath, appStylePath);
    myFixture.copyFileToProject(appLayoutLocalPath, appLayoutPath);

    doTestErrorMessageShown(false, true, false, libModuleDir + "/res/layout");

    myFixture.checkResultByFile(libStylesPath, stylesLocalPath, true);
    myFixture.checkResultByFile(appStylePath, stylesLocalPath, true);
    myFixture.checkResultByFile(appLayoutPath, appLayoutLocalPath, true);

    doTest(true, libModuleDir + "/res/layout");
  }

  public void test16() {
    final String libModuleDir = getContentRootPath("lib");
    final String libStylesPath = libModuleDir + "/res/values/styles.xml";
    final String appLayoutPath = "res/layout/layout.xml";

    myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "_styles.xml", libStylesPath);
    myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "_1.xml", appLayoutPath);

    final String testName = getTestName(true);
    final VirtualFile f = myFixture
      .copyFileToProject(BASE_PATH + testName + ".xml", libModuleDir + "/res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    doInlineStyleReferenceAction(false, true);

    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
    myFixture.checkResultByFile(appLayoutPath, BASE_PATH + getTestName(true) + "_1_after.xml", true);
    myFixture.checkResultByFile(libStylesPath, BASE_PATH + getTestName(true) + "_styles_after.xml", true);
  }

  public void test17() {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + testName + "_1.xml", "res/layout/test1.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    doCommonInlineAction(true);
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
    myFixture.checkResultByFile("res/layout/test1.xml", BASE_PATH + testName + "_1.xml", true);
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + testName + "_styles.xml", true);
  }

  private void doCommonInlineAction(boolean inlineThisOnly) {
    AndroidInlineStyleHandler.setTestConfig(new AndroidInlineTestConfig(inlineThisOnly));
    try {
      final Presentation p = myFixture.testAction(new InlineAction());
      assertTrue(p.isEnabled());
      assertTrue(p.isVisible());
    }
    finally {
      AndroidInlineStyleHandler.setTestConfig(null);
    }
  }

  public void test18() {
    final String testName = getTestName(true);
    final String stylesPath = BASE_PATH + testName + "_styles.xml";
    myFixture.copyFileToProject(stylesPath, "res/values/styles.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    try {
      doCommonInlineAction(false);
      fail();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
    }
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
    myFixture.checkResultByFile("res/values/styles.xml", stylesPath, true);
  }

  public void test19() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + "_styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.copyFileToProject(BASE_PATH + testName + "_1.xml", "res/layout/test1.xml");
    myFixture.configureFromExistingVirtualFile(f);
    doCommonInlineAction(false);
    myFixture.checkResultByFile(BASE_PATH + testName + "_styles_after.xml", true);
    myFixture.checkResultByFile("res/layout/test.xml", BASE_PATH + testName + "_after.xml", true);
    myFixture.checkResultByFile("res/layout/test1.xml", BASE_PATH + testName + "_1_after.xml", true);
  }

  public void test20() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + "_styles.xml", "res/values/styles.xml");
    final String layoutPath = BASE_PATH + testName + ".xml";
    myFixture.copyFileToProject(layoutPath, "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    try {
      doCommonInlineAction(false);
      fail();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
    }
    myFixture.checkResultByFile(BASE_PATH + testName + "_styles_after.xml", true);
    myFixture.checkResultByFile("res/layout/test.xml", layoutPath, true);
  }

  public void test21() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + "_styles.xml", "res/values/styles.xml");
    final String layoutPath = BASE_PATH + testName + ".xml";
    myFixture.copyFileToProject(layoutPath, "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    try {
      doCommonInlineAction(false);
      fail();
    }
    catch (IncorrectOperationException e) {
      assertTrue(e.getMessage().length() > 0);
    }
  }

  public void test22() {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    try {
      myFixture.testAction(new AndroidInlineStyleReferenceAction(new AndroidInlineTestConfig(false)));
      fail();
    }
    catch (IncorrectOperationException e) {
      assertTrue(e.getMessage().length() > 0);
    }
  }

  public void test23() {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + testName + "_1.xml", "res/layout/test1.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    doCommonInlineAction(false);
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
    myFixture.checkResultByFile("res/layout/test1.xml", BASE_PATH + testName + "_1_after.xml", true);
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + testName + "_styles_after.xml", true);
  }

  public void test24() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/values/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    doCommonInlineAction(true);
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
  }

  public void test25() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/values/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    doInlineStyleReferenceAction(true, true);
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
  }

  public void test26() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/values/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    doInlineStyleReferenceAction(false, true);
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
  }

  public void test27() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/values/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    doInlineStyleReferenceAction(false, false);
  }

  public void test28() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/values/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    doCommonInlineAction(false);
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
  }

  private void doTest(boolean inlineThisOnly) {
    doTest(inlineThisOnly, "res/layout");
  }

  private void doTest(boolean inlineThisOnly, String dirToCopy) {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_styles.xml", "res/values/styles.xml");
    final VirtualFile f = myFixture
      .copyFileToProject(BASE_PATH + testName + ".xml", dirToCopy + "/test" + testName + "_" + Boolean.toString(inlineThisOnly) + ".xml");
    myFixture.configureFromExistingVirtualFile(f);
    doInlineStyleReferenceAction(inlineThisOnly, true);
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + testName + (
      inlineThisOnly ? "_styles.xml" : "_styles_after.xml"), true);
  }

  private void doInlineStyleReferenceAction(boolean inlineThisOnly, boolean enabled) {
    final Presentation p =
      myFixture.testAction(new AndroidInlineStyleReferenceAction(new AndroidInlineTestConfig(inlineThisOnly)));
    assertEquals(enabled, p.isEnabled());
    assertTrue(p.isVisible());
  }

  private void doTestErrorMessageShown(boolean testInlineThis, boolean testInlineAll, boolean copyStyles, String dirToCopy) {
    if (copyStyles) {
      myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "_styles.xml",
                                  "res/values/styles.xml");
    }
    if (testInlineThis) {
      doTestErrorMessageShown(true, copyStyles, dirToCopy);
    }

    if (testInlineAll) {
      doTestErrorMessageShown(false, copyStyles, dirToCopy);
    }
  }

  private void doTestErrorMessageShown(boolean testInlineThis, boolean testInlineAll, boolean copyStyles) {
    doTestErrorMessageShown(testInlineThis, testInlineAll, copyStyles, "res/layout");
  }

  private void doTestErrorMessageShown(boolean inlineThisOnly, boolean checkStylesXml, String dirToCopy) {
    try {
      performAction(inlineThisOnly, dirToCopy);
      fail();
    }
    catch (IncorrectOperationException e) {
      assertTrue(e.getMessage().length() > 0);
      final String testName = getTestName(true);
      myFixture.checkResultByFile(BASE_PATH + testName + ".xml");
      if (checkStylesXml) {
        myFixture.checkResultByFile("res/values/styles.xml",
                                    BASE_PATH + testName + "_styles.xml", true);
      }
    }
  }

  private void doTestDisabled() {
    final Presentation presentation = performAction(true, "res/layout");
    assertFalse(presentation.isEnabled());
    assertTrue(presentation.isVisible());
  }

  private Presentation performAction(boolean inlineThisOnly, String dirToCopy) {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml",
                                                      dirToCopy + "/test" + testName + "_" +
                                                      Boolean.toString(inlineThisOnly) + ".xml");
    myFixture.configureFromExistingVirtualFile(f);
    return myFixture.testAction(new AndroidInlineStyleReferenceAction(new AndroidInlineTestConfig(inlineThisOnly)));
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    final String testName = getTestName(true);
    if (testName.equals("14") || testName.equals("15") || testName.equals("16")) {
      addModuleWithAndroidFacet(projectBuilder, modules, "lib", true);
    }
  }
}
