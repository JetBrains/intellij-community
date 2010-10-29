package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author peter
 */
public class GroovyCompletionConfidence extends CompletionConfidence {
  @Override
  public Boolean shouldFocusLookup(@NotNull CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();
    if (position.getParent() instanceof GrReferenceExpression) {
      final GrExpression expression = ((GrReferenceExpression)position.getParent()).getQualifierExpression();
      if (expression == null) {
        return true;
      }
      if (expression.getType() == null) {
        return false;
      }
      return true;
    }
    return null;
  }
}
