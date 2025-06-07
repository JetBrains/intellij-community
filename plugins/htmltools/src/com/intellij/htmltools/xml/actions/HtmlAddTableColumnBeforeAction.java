package com.intellij.htmltools.xml.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.htmltools.html.actions.TableColumnAdder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class HtmlAddTableColumnBeforeAction extends CodeInsightAction {
  private final CodeInsightActionHandler myHandler;

  public HtmlAddTableColumnBeforeAction() {
    myHandler = new CodeInsightActionHandler() {
      @Override
      public void invoke(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
        TableColumnAdder.addColumn(project, editor, psiFile, true);
      }
    };
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return myHandler;
  }

  @Override
  protected boolean isValidForFile(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
    return TableColumnAdder.isActionAvailable(editor, psiFile);
  }
}
