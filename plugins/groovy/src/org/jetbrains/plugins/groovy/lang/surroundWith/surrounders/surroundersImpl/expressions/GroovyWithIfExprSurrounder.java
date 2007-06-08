package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithIfExprSurrounder extends GroovyExpressionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    if (isNeedsParentheses(node)) return "if " + "(" + "(" + node.getText() + ")" + ") {4 \n }";
    else return "if " + "(" + node.getText() + ") {4 \n }";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrIfStatement;

    GrIfStatement grIfStatement = (GrIfStatement) element;
    GroovyPsiElement psiElement = grIfStatement.getThenBranch();


    assert psiElement instanceof GrOpenBlock;
    GrStatement[] grStatements = ((GrOpenBlock) psiElement).getStatements();
    assert grStatements.length > 0;

    GrStatement grStatement = grStatements[0];
    int endOffset = grStatement.getTextRange().getStartOffset();
    grStatement.getNode().getTreeParent().removeChild(grStatement.getNode());

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "if (...) {}";
  }

  protected boolean isApplicable(PsiElement element) {
    return element instanceof GrConditionalExpression && PsiType.BOOLEAN.getPresentableText().toLowerCase().equals(((GrExpression) element).getType().getPresentableText().toLowerCase());
  }
}
