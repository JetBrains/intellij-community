package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovySurrounderByClosure implements Surrounder {
  public String getTemplateDescription() {
    return "{ }";
  }

  public boolean isApplicable(@NotNull PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!isApplicable(element)) {
        return false;
      }
    }
    return true;
  }

  protected boolean isApplicable(PsiElement element) {
    return element.getParent() instanceof GroovyFile
        || element.getParent() instanceof GrMethod;
  }

  @Nullable
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) throws IncorrectOperationException {
    if (elements.length == 0) return null;

    StringBuffer elementsBuffer = new StringBuffer();

    elementsBuffer.append("{");
    for (PsiElement element : elements) {
      elementsBuffer.append(element.getText());
      elementsBuffer.append("\n");
    }
    elementsBuffer.append("}");

    PsiElement closure = GroovyElementFactory.getInstance(project).createClosureFromText(elementsBuffer.toString());
    assert closure != null;

    elements[0].getParent().replace(closure);

    return null;
  }
}