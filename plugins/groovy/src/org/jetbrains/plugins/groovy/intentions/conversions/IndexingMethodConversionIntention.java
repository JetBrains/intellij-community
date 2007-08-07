package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;

public class IndexingMethodConversionIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new IndexingMethodConversionPredicate();
  }

  public void processIntention(@NotNull PsiElement element)
      throws IncorrectOperationException {
    final GrMethodCallExpression callExpression =
        (GrMethodCallExpression) element;
      final GrArgumentList argList = callExpression.getArgumentList();
      final GrExpression[] arguments = argList.getExpressionArguments();

      GrReferenceExpression methodExpression = (GrReferenceExpression) callExpression.getInvokedExpression();
      final IElementType referenceType = methodExpression.getDotTokenType();

      final String methodName = methodExpression.getName();
      final GrExpression qualifier = methodExpression.getQualifierExpression();
      if("getAt".equals(methodName)|| "get".equals(methodName))
      {
          replaceExpression(qualifier.getText() + '[' + arguments[0].getText() + ']',
                  callExpression);
      }
      else{
          replaceExpression(qualifier.getText() + '[' + arguments[0].getText() + "]=" +arguments[1].getText(),
                  callExpression);
      }
  }

}
