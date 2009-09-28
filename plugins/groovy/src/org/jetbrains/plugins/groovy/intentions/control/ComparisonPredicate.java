package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.intentions.utils.ComparisonUtils;

class ComparisonPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrBinaryExpression)) {
      return false;
    }
    final GrBinaryExpression expression = (GrBinaryExpression) element;
    if (!ComparisonUtils.isComparison(expression)) {
      return false;
    }
    return !ErrorUtil.containsError(element);
  }
}
