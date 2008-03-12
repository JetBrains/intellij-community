package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithIfElseExprSurrounder extends GroovyConditionSurrounder {
  protected TextRange surroundExpression(GrExpression expression) {
    GrIfStatement ifStatement = (GrIfStatement) GroovyPsiElementFactory.getInstance(expression.getProject()).createTopElementFromText("if(a){4\n} else{\n}");
    replaceToOldExpression(expression, (GrExpression) ifStatement.getCondition());
    expression.replaceWithStatement(ifStatement);
    GrStatement psiElement = ifStatement.getThenBranch();


    assert psiElement instanceof GrBlockStatement;
    GrStatement[] statements = ((GrBlockStatement) psiElement).getBlock().getStatements();
    assert statements.length > 0;

    GrStatement statement = statements[0];
    int endOffset = statement.getTextRange().getStartOffset();
    statement.getNode().getTreeParent().removeChild(statement.getNode());

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "if (...) / else";
  }
}
