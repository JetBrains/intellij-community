package com.intellij.htmltools.xml.actions.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.htmltools.html.actions.TableCellNavigator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class HtmlTableCellNavigateUpAction extends CodeInsightAction {
  private final CodeInsightActionHandler myHandler;

  public HtmlTableCellNavigateUpAction() {
    myHandler = new CodeInsightActionHandler() {
      @Override
      public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
        TableCellNavigator.moveCaret(project, editor, file, TableCellNavigator.Directions.UP);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }
  @Override
  @NotNull
  protected final CodeInsightActionHandler getHandler() {
    return myHandler;
  }

  @Override
  public boolean isValidForFile(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    return TableCellNavigator.isActionAvailable(editor, file);
  }
}
