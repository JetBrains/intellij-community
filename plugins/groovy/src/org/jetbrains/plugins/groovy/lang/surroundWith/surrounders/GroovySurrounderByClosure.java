package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovySurrounderByClosure extends GroovyManyStatementsSurrounder {
  public String getTemplateDescription() {
    return "{ -> ... }.call()";
  }

  protected String getElementsTemplateAsString(ASTNode[] nodes) {
    return "{ -> \n" + getListElementsTemplateAsString(nodes) + "}.call()";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrMethodCallExpression;

    final GrExpression invoked = ((GrMethodCallExpression) element).getInvokedExpression();
    assert invoked instanceof GrReferenceExpression;
    final int offset = ((GrReferenceExpression) invoked).getReferenceNameElement().getTextRange().getStartOffset();
    return new TextRange(offset, offset);
  }
}