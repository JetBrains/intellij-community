package org.jetbrains.android.intentions;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAddStringResourceActionTest extends AndroidTestCase {
  private static final String BASE_PATH = "addStringRes/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(true);
  }

  public void test1() {
    doTest();
  }

  public void test2() {
    doTest();
  }

  public void test3() {
    doTest();
  }

  public void test4() {
    doTest();
  }

  public void test5() {
    doTest(new Runnable() {
      @Override
      public void run() {
        myFixture.type('r');
        TemplateManagerImpl.getTemplateState(myFixture.getEditor()).nextTab();
      }
    });
  }

  public void test6() {
    doTest();
  }

  public void test7() {
    doTest(new Runnable() {
      @Override
      public void run() {
        myFixture.type('c');
        TemplateManagerImpl.getTemplateState(myFixture.getEditor()).nextTab();
      }
    });
  }

  public void test8() {
    doTest(new Runnable() {
      @Override
      public void run() {
        myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM);
      }
    });
  }

  public void test9() {
    doTest();
  }

  public void test10() {
    doTest();
  }

  public void test11() {
    doTest(new Runnable() {
      @Override
      public void run() {
        myFixture.type('c');
        myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM);
      }
    });
  }

  public void test12() {
    doTest();
  }

  public void test13() {
    doTest(new Runnable() {
      @Override
      public void run() {
        myFixture.type("r");
        myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM);
      }
    });
  }

  public void test14() {
    VirtualFile javaFile = myFixture.copyFileToProject(BASE_PATH + "Class14.java", "src/p1/p2/Class.java");
    myFixture.configureFromExistingVirtualFile(javaFile);
    final PsiFile javaPsiFile = myFixture.getFile();
    assertFalse(new AndroidAddStringResourceAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), javaPsiFile));
  }

  public void testEscape() {
    doTest(getTestName(false), "strings.xml", null, true, "strings_escape_after.xml");
  }

  public void testNewFile() {
    doTest("1", null, null, true);
  }

  public void testInvalidStringsXml() {
    try {
      doTest("1", "strings_invalid.xml", null, true);
      fail();
    }
    catch (IncorrectOperationException e) {
      // in normal mode error dialog will be shown
      assertEquals("invalid strings.xml", e.getMessage());
    }
    myFixture.checkResultByFile(BASE_PATH + "Class1.java");
    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + "strings_invalid.xml", false);
  }

  private void doTest() {
    doTest(getTestName(false), "strings.xml", null, true);
  }

  private void doTest(Runnable invokeAfterTemplate) {
    doTest(getTestName(false), "strings.xml", invokeAfterTemplate, false);
  }

  private void doTest(String testName, String stringsXml, final Runnable invokeAfterTemplate, final boolean closePopup) {
    doTest(testName, stringsXml, invokeAfterTemplate, closePopup, "strings_after.xml");
  }

  private void doTest(String testName,
                      String stringsXml,
                      @Nullable final Runnable invokeAfterTemplate,
                      final boolean closePopup,
                      String stringsAfter) {
    if (stringsXml != null) {
      myFixture.copyFileToProject(BASE_PATH + stringsXml, "res/values/strings.xml");
    }
    myFixture.copyFileToProject(BASE_PATH + "R.java", "src/p1/p2/R.java");
    VirtualFile javaFile = myFixture.copyFileToProject(BASE_PATH + "Class" + testName + ".java", "src/p1/p2/Class.java");
    myFixture.configureFromExistingVirtualFile(javaFile);
    final PsiFile javaPsiFile = myFixture.getFile();
    assertTrue(new AndroidAddStringResourceAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), javaPsiFile));
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            AndroidAddStringResourceAction.doInvoke(myFixture.getProject(), myFixture.getEditor(), javaPsiFile, "hello");
            if (invokeAfterTemplate != null) {
              invokeAfterTemplate.run();
            }
          }
        });
        if (closePopup) {
          myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
        }
      }
    }, "", "");
    myFixture.checkResultByFile(BASE_PATH + "Class" + testName + "_after.java");
    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + stringsAfter, false);
  }
}
