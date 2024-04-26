package com.intellij.htmltools.xml.actions.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.htmltools.html.actions.TableCellNavigator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class HtmlTableCellNavigateLeftAction extends CodeInsightAction {
  private final CodeInsightActionHandler myHandler;

  public HtmlTableCellNavigateLeftAction() {
    myHandler = new CodeInsightActionHandler() {
      @Override
      public void invoke(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
        TableCellNavigator.moveCaret(project, editor, file, TableCellNavigator.Directions.LEFT);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return myHandler;
  }

  @Override
  protected boolean isValidForFile(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
    return TableCellNavigator.isActionAvailable(editor, file);
  }
}
