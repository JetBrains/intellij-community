package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;

class ConjunctionPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrBinaryExpression)) {
      return false;
    }
    final GrBinaryExpression expression = (GrBinaryExpression) element;
    final IElementType tokenType =  expression.getOperationTokenType();
    if (tokenType == null) return false;
    if (!tokenType.equals(GroovyTokenTypes.mLAND) &&
        !tokenType.equals(GroovyTokenTypes.mLOR)) {
      return false;
    }
    return !ErrorUtil.containsError(element);
  }
}
