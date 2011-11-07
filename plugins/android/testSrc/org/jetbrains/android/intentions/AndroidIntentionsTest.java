package org.jetbrains.android.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidIntentionsTest extends AndroidTestCase {
  private static final String BASE_PATH = "intentions/";

  public void testSwitchOnResourceId() {
    doTestSwitchOnResourceId(true);
  }

  public void testSwitchOnResourceId2() {
    doTestSwitchOnResourceId(true);
  }

  public void testSwitchOnResourceId3() {
    doTestSwitchOnResourceId(true);
  }

  public void testSwitchOnResourceId4() {
    doTestSwitchOnResourceId(false);
  }

  public void testSwitchOnResourceId5() {
    doTestSwitchOnResourceId(false);
  }

  public void testSwitchOnResourceId6() {
    doTestSwitchOnResourceId(false);
  }

  private void doTestSwitchOnResourceId(boolean available) {
    myFacet.getConfiguration().LIBRARY_PROJECT = true;
    myFixture.copyFileToProject(BASE_PATH + "R.java", "src/p1/p2/R.java");
    doTest(new AndroidReplaceSwitchWithIfIntention(), available);
  }

  private void doTest(final IntentionAction intention, boolean available) {
    VirtualFile javaFile = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/Class.java");
    myFixture.configureFromExistingVirtualFile(javaFile);

    final boolean actualAvailable = intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());

    if (available) {
      assertTrue(actualAvailable);

      CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              intention.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
            }
          });
        }
      }, "", "");

      myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
    }
    else {
      assertFalse(actualAvailable);
    }
  }
}
