package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithWhileExprSurrounder extends GroovyConditionSurrounder {
  protected TextRange surroundExpression(GrExpression expression) {
    GrWhileStatement whileStatement = (GrWhileStatement) GroovyPsiElementFactory.getInstance(expression.getProject()).createTopElementFromText("while(a){4\n}");
    replaceToOldExpression((GrExpression)whileStatement.getCondition(), expression);
    expression.replaceWithStatement(whileStatement);
    GrStatement body = whileStatement.getBody();

    assert body instanceof GrBlockStatement;
    GrStatement[] statements = ((GrBlockStatement) body).getBlock().getStatements();
    assert statements.length > 0;

    GrStatement statement = statements[0];
    int offset = statement.getTextRange().getStartOffset();
    statement.getNode().getTreeParent().removeChild(statement.getNode());

    return new TextRange(offset, offset);
  }

  public String getTemplateDescription() {
    return "while (...) {}";
  }
}
