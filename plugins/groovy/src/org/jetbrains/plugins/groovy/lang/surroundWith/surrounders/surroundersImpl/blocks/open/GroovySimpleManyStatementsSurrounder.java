package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovyManyStatementsSurrounder;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.util.TextRange;

/**
 * This is a base class for simple "Many Statement" surrounds, such as with() and shouldFail().
 *
 * User: Hamlet D'Arcy
 * Date: Mar 18, 2009
 */
public abstract class GroovySimpleManyStatementsSurrounder extends GroovyManyStatementsSurrounder {
  protected final GroovyPsiElement doSurroundElements(PsiElement[] elements) throws IncorrectOperationException {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(elements[0].getProject());
    GrMethodCallExpression withCall = (GrMethodCallExpression) factory.createTopElementFromText(getReplacementTokens());
    addStatements(withCall.getClosureArguments()[0], elements);
    return withCall;
  }

  protected abstract String getReplacementTokens();

  protected final TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrMethodCallExpression;

    GrMethodCallExpression withCall = (GrMethodCallExpression) element;
    GrCondition condition = withCall.getExpressionArguments()[0];
    int endOffset = condition.getTextRange().getStartOffset();

    condition.getParent().getNode().removeChild(condition.getNode());

    return new TextRange(endOffset, endOffset);
  }

}
