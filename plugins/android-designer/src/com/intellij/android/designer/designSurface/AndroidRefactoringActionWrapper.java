package com.intellij.android.designer.designSurface;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidRefactoringActionWrapper extends AnAction {
  private final AnAction myWrappee;

  public AndroidRefactoringActionWrapper(@NotNull String text, @NotNull AnAction wrappee) {
    super(text, null, null);
    myWrappee = wrappee;
    getTemplatePresentation().setDescription(wrappee.getTemplatePresentation().getDescription());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myWrappee.actionPerformed(e);
  }

  @Override
  public void update(AnActionEvent e) {
    myWrappee.update(e);
    final Presentation p = e.getPresentation();
    if (!p.isVisible()) {
      p.setEnabled(false);
      p.setVisible(true);
    }
  }
}
