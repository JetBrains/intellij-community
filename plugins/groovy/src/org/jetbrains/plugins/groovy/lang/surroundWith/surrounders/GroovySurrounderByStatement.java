package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
abstract public class GroovySurrounderByStatement implements Surrounder {
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!isApplicable(element)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) throws IncorrectOperationException{
    if (elements.length == 0) return null;

    GroovyPsiElement expression = null;
    for (PsiElement element : elements) {
      expression = GroovyElementFactory.getInstance(project).createTopElementFromText(getExpressionTemplateAsString(element.getNode()));

      elements[0].getParent().replace(expression);
    }

    assert expression != null;
    return getSurroundSelectionRange(expression);
  }

  protected abstract String getExpressionTemplateAsString(ASTNode node);

  protected abstract TextRange getSurroundSelectionRange(GroovyPsiElement element);

  protected abstract boolean isApplicable(PsiElement element);
}