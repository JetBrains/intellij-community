package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrTypeCast;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithTypeCastSurrounder extends GroovyExpressionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    return "(" + "(" + "Type" + ")" + node.getText() + ")";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrTypeCast;

    GrTypeCast typeCast = (GrTypeCast) element;
    int endOffset = typeCast.getExpression().getTextRange().getEndOffset();
    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return " ((Type) expression)";
  }
}
