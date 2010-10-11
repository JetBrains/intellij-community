package org.jetbrains.javafx;

import com.intellij.codeInsight.generation.actions.CommentByBlockCommentAction;
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.javafx.testUtils.JavaFxLightFixtureTestCase;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxCommenterTest extends JavaFxLightFixtureTestCase {
  private static void performBlockCommentAction() {
    CommentByBlockCommentAction action = new CommentByBlockCommentAction();
    action.actionPerformed(new AnActionEvent(
      null,
      DataManager.getInstance().getDataContext(),
      "",
      action.getTemplatePresentation(),
      ActionManager.getInstance(),
      0)
    );
  }

  private static void performLineCommentAction() {
    CommentByLineCommentAction action = new CommentByLineCommentAction();
    action.actionPerformed(new AnActionEvent(
      null,
      DataManager.getInstance().getDataContext(),
      "",
      action.getTemplatePresentation(),
      ActionManager.getInstance(),
      0)
    );
  }

  public void testSimple() throws Exception {
    doLineCommentTest();
  }

  public void testSimpleUncomment() throws Exception {
    doLineCommentTest();
  }

  public void testMultiline() throws Exception {
    doLineCommentTest();
  }

  public void testMultilineUncomment() throws Exception {
    doLineCommentTest();
  }

  public void testBlock() throws Exception {
    doBlockCommentTest();
  }

  public void testBlockUncomment() throws Exception {
    doBlockCommentTest();
  }

  private void doLineCommentTest() throws Exception {
    final String name = getTestName(false);
    myFixture.configureByFile("/commenter/" + name + ".fx");
    performLineCommentAction();
    myFixture.checkResultByFile("/commenter/" + name + "_after.fx", true);
  }

  private void doBlockCommentTest() throws Exception {
    final String name = getTestName(false);
    myFixture.configureByFile("/commenter/" + name + ".fx");
    performBlockCommentAction();
    myFixture.checkResultByFile("/commenter/" + name + "_after.fx", true);
  }
}
