package org.jetbrains.plugins.groovy.intentions.base;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

public abstract class MutablyNamedIntention extends Intention {
  private String text = null;

  protected abstract String getTextForElement(PsiElement element);

  @NotNull
  public String getText() {
    return text;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiElement element = findMatchingElement(file, editor);
    if (element != null) {
      text = getTextForElement(element);
    }
    return element != null;
  }
}
