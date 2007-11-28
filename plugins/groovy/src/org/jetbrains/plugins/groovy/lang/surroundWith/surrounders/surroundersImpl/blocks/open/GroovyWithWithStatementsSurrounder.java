package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovyManyStatementsSurrounder;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithWithStatementsSurrounder extends GroovyManyStatementsSurrounder {
  protected GroovyPsiElement doSurroundElements(PsiElement[] elements) throws IncorrectOperationException {
    GroovyElementFactory factory = GroovyElementFactory.getInstance(elements[0].getProject());
    GrMethodCallExpression withCall = (GrMethodCallExpression) factory.createTopElementFromText("with(a){\n}");
    addStatements(withCall.getClosureArguments()[0], elements);
    return withCall;
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrMethodCallExpression;

    GrMethodCallExpression withCall = (GrMethodCallExpression) element;
    GrCondition condition = withCall.getExpressionArguments()[0];
    int endOffset = condition.getTextRange().getStartOffset();

    condition.getParent().getNode().removeChild(condition.getNode());

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "with () {...}";
  }    
}
