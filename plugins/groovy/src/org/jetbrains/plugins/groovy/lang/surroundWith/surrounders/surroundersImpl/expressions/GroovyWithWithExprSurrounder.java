package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithWithExprSurrounder extends GroovyExpressionSurrounder {
  protected TextRange surroundExpression(GrExpression expression) {
    GrMethodCallExpression call = (GrMethodCallExpression) GroovyPsiElementFactory.getInstance(expression.getProject()).createTopElementFromText("with(a){4\n}");
    replaceToOldExpression(call.getExpressionArguments()[0], expression);
    call = expression.replaceWithStatement(call);
    GrClosableBlock block = call.getClosureArguments()[0];

    GrStatement statementInBody = block.getStatements()[0];
    int offset = statementInBody.getTextRange().getStartOffset();

    statementInBody.getParent().getNode().removeChild(statementInBody.getNode());

    return new TextRange(offset, offset);
  }

  public String getTemplateDescription() {
    return "with (...)";
  }
}