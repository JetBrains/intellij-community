package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.navigation.CtrlMouseData;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class JourneyGotoDeclarationAction extends GotoDeclarationAction implements JourneyEditorOverrideActionPromoter {

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new JourneyGotoDeclarationOnlyHandler();
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler(@NotNull DataContext dataContext) {
    return new JourneyGotoDeclarationOnlyHandler();
  }

  @Override
  public CtrlMouseData getCtrlMouseData(@NotNull Editor editor, @NotNull PsiFile file, int offset) {
    return super.getCtrlMouseData(editor, file, offset);
  }

}
