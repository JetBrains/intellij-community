package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyConditionSurrounder;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithIfExprSurrounder extends GroovyConditionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    if (isNeedsParentheses(node)) return "if " + "(" + "(" + node.getText() + ")" + ") {4 \n }";
    else return "if " + "(" + node.getText() + ") {4 \n }";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrIfStatement;

    GrIfStatement grIfStatement = (GrIfStatement) element;
    GroovyPsiElement psiElement = grIfStatement.getThenBranch();


    assert psiElement instanceof GrBlockStatement;
    GrStatement[] grStatements = ((GrBlockStatement) psiElement).getBlock().getStatements();
    assert grStatements.length > 0;

    GrStatement grStatement = grStatements[0];
    int endOffset = grStatement.getTextRange().getStartOffset();
    grStatement.getNode().getTreeParent().removeChild(grStatement.getNode());

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "if (...) {}";
  }
}
