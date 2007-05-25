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
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class GroovyManyStatementsSurrounder implements Surrounder {

  public boolean isApplicable(@NotNull PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!isApplicable(element)) {
        return false;
      }
    }
    return true;
  }

  protected String getListElementsTemplateAsString(ASTNode... nodes) {
    StringBuffer result = new StringBuffer();
    for (ASTNode node : nodes) {
      result.append(node.getText());
      result.append("\n");
    }
    return result.toString();
  }

  @Nullable
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) throws IncorrectOperationException {
    if (elements.length == 0) return null;

//    StringBuffer elementsBuffer = new StringBuffer();
    ASTNode[] nodes = new ASTNode[elements.length];

    for (int i = 0; i < elements.length; i++) {
      nodes[i] = elements[i].getNode();
    }

    GroovyPsiElement newStmt = GroovyElementFactory.getInstance(project).createTopElementFromText(getElementsTemplateAsString(nodes));
    assert newStmt != null;

    PsiElement element1 = elements[0];
    ASTNode parentNode = element1.getParent().getNode();


    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];

      if (i == 0) {
        parentNode.replaceChild(element1.getNode(), newStmt.getNode());
      } else {
        assert element1.getParent() == element.getParent();
        parentNode.removeChild(element.getNode());
      }
    }

    return getSurroundSelectionRange(newStmt);
  }

  protected abstract String getElementsTemplateAsString(ASTNode... node);

  protected abstract TextRange getSurroundSelectionRange(GroovyPsiElement element);

  protected abstract boolean isApplicable(PsiElement element);
}