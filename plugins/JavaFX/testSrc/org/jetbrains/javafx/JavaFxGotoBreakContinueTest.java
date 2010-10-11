package org.jetbrains.javafx;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.PsiElement;
import org.jetbrains.javafx.testUtils.JavaFxLightFixtureTestCase;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxGotoBreakContinueTest extends JavaFxLightFixtureTestCase {
  private void doTest() throws Exception {
    final String fileName = "/goto/" + getTestName(false);
    myFixture.configureByFile(fileName + ".fx");
    performAction();
    myFixture.checkResultByFile(fileName + "_after.fx");
  }

  private void performAction() {
    final Editor editor = myFixture.getEditor();
    final PsiElement element = GotoDeclarationAction.findTargetElement(myFixture.getProject(), editor,
                                             editor.getCaretModel().getOffset());
    assertEquals(myFixture.getFile(), element.getContainingFile());
    editor.getCaretModel().moveToOffset(element.getTextOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    editor.getSelectionModel().removeSelection();
  }

  public void testContinueWhile() throws Exception {
    doTest();
  }

  public void testContinueFor() throws Exception {
    doTest();
  }

  public void testContinueInsideVar() throws Exception {
    doTest();
  }

  public void testBreakWhile() throws Exception {
    doTest();
  }

  public void testBreakFor() throws Exception {
    doTest();
  }

  public void testBreakInsideVar() throws Exception {
    doTest();
  }

  public void testBreakEof() throws Exception {
    doTest();
  }

  public void testNestedLoop() throws Exception {
    doTest();
  }
}
