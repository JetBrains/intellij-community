package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.05.2007
 */
public class GroovyWithIfSurrounder extends GroovyOpenBlockSurrounder {
  protected String getElementsTemplateAsString(ASTNode[] nodes) {
    return "if (a) { \n" + getListElementsTemplateAsString(nodes) + "}";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrIfStatement;
    GrCondition condition = ((GrIfStatement) element).getCondition();

    int endOffset = element.getTextRange().getEndOffset();
    if (condition != null) {
      PsiElement child = condition.getFirstChild();
      assert child != null;

      endOffset = child.getTextRange().getStartOffset();
      condition.getParent().getNode().removeChild(condition.getNode());
    }
    return new TextRange(endOffset, endOffset);
  }

  protected boolean isApplicable(PsiElement element) {
    return element instanceof GrStatement;
  }

  public String getTemplateDescription() {
    return "if () {...}";
  }
}
