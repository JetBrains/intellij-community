package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithWhileExprSurrounder extends GroovyExpressionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    return "while " + "(" + node.getText() + ") { 4 \n }";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrWhileStatement;

    GrWhileStatement grWhileStatement = (GrWhileStatement) element;
    GrCondition grStatement = grWhileStatement.getBody();

    int endOffset = grStatement.getTextRange().getEndOffset();

    if (grStatement instanceof GrStatement) {
      endOffset = grStatement.getTextRange().getEndOffset();
      grStatement.getParent().getNode().removeChild(grStatement.getNode());

    } else if (grStatement instanceof GrOpenBlock) {
      GrStatement grStatementInBody = ((GrOpenBlock) grStatement).getStatements()[0];
      endOffset = grStatementInBody.getTextRange().getEndOffset();

      grStatementInBody.getParent().getNode().removeChild(grStatementInBody.getNode());
    }

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "while (...) {}";
  }

  protected boolean isApplicable(PsiElement element) {
    return element instanceof GrExpression && PsiType.BOOLEAN.getPresentableText().toLowerCase().equals(((GrExpression) element).getType().getPresentableText().toLowerCase());
  }
}
