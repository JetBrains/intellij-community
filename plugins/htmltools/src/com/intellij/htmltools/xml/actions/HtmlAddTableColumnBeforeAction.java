package com.intellij.htmltools.xml.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.htmltools.html.actions.TableColumnAdder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class HtmlAddTableColumnBeforeAction extends CodeInsightAction {
  private final CodeInsightActionHandler myHandler;

  public HtmlAddTableColumnBeforeAction() {
    myHandler = new CodeInsightActionHandler() {
      @Override
      public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
        TableColumnAdder.addColumn(project, editor, file, true);
      }
    };
  }

  @Override
  @NotNull
  protected final CodeInsightActionHandler getHandler() {
    return myHandler;
  }

  @Override
  protected boolean isValidForFile(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    return TableColumnAdder.isActionAvailable(editor, file);
  }
}
