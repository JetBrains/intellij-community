package com.intellij.codeInsight;

import com.intellij.codeInsight.generation.actions.CommentByBlockCommentAction;
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

public class CommentInCustomFileTypesTest extends LightPlatformCodeInsightTestCase {
  public CommentInCustomFileTypesTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath();
  }

  public void testBlockComment() throws Exception {
    configureByFile("/codeInsight/commentInCustomFileType/block1.cs");
    performBlockCommentAction();
    checkResultByFile("/codeInsight/commentInCustomFileType/block1_after.cs");

    configureByFile("/codeInsight/commentInCustomFileType/block1_after.cs");
    performBlockCommentAction();
    checkResultByFile("/codeInsight/commentInCustomFileType/block1_after2.cs");
  }

  public void testLineComment() throws Exception {
    configureByFile("/codeInsight/commentInCustomFileType/line1.cs");
    performLineCommentAction();
    checkResultByFile("/codeInsight/commentInCustomFileType/line1_after.cs");

    configureByFile("/codeInsight/commentInCustomFileType/line2.cs");
    performLineCommentAction();
    checkResultByFile("/codeInsight/commentInCustomFileType/line2_after.cs");
  }

  private void performBlockCommentAction() {
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

  private void performLineCommentAction() {
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
}
