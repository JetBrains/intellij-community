package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrParenthesizedExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrTypeCast;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpr;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithTypeCastSurrounder extends GroovyExpressionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    return isNeedsParentheses(node) ? "(" + "(" + "Type" + ") " + "(" + node.getText() + ")" + ")"
        : "(" + "(" + "Type" + ") " + node.getText() + ")";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrParenthesizedExpr;

    GrParenthesizedExpr grParenthesizedExpr = (GrParenthesizedExpr) element;
    GrTypeCastExpression typeCast = (GrTypeCastExpression) grParenthesizedExpr.getOperand();

    GrTypeElement grTypeElement = typeCast.getCastTypeElement();
    int endOffset = grTypeElement.getTextRange().getStartOffset();

    typeCast.getNode().removeChild(grTypeElement.getNode());

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "((Type) ...)";
  }
}
