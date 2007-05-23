package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovySurrounderByParametrizedClosure implements Surrounder {
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
        || element.getParent() instanceof GrOpenBlock;
  }

  @Nullable
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) throws IncorrectOperationException {
    if (elements.length == 0) return null;

    PsiElement elementsParent = elements[0].getParent();

    assert elementsParent != null;

    StringBuffer elementsBuffer = new StringBuffer();

    elementsBuffer.append("{ \n");
    elementsBuffer.append("it -> \n");
    for (PsiElement element : elements) {
      elementsBuffer.append(element.getText());
      elementsBuffer.append("\n");
    }
    elementsBuffer.append("}");

    PsiElement closure = GroovyElementFactory.getInstance(project).createClosureFromText(elementsBuffer.toString());

    int i = 0;
    while (i < elements.length) {
      PsiElement element = elements[i];

      assert elementsParent == element.getParent();
      if (i == 0) {
        elementsParent.getNode().replaceChild(element.getNode(), closure.getNode());
      } else {
        elementsParent.getNode().removeChild(element.getNode());
      }
      i++;
    }

    assert closure != null;

//    elementsParent.getNode().addChild(closure.getNode());

    return new TextRange(closure.getTextRange().getEndOffset(), closure.getTextRange().getEndOffset());
  }
}