package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyMethodInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrCallExpressionTypeCalculator;

/**
 * @author Sergey Evdokimov
 */
public class GrDescriptorReturnTypeCalculator extends GrCallExpressionTypeCalculator {

  @Override
  public PsiType calculateReturnType(@NotNull GrMethodCall callExpression, @NotNull PsiMethod method) {
    for (GroovyMethodInfo methodInfo : GroovyMethodInfo.getInfos(method)) {
      String returnType = methodInfo.getReturnType();
      if (returnType != null) {
        if (methodInfo.isApplicable(method)) {
          return JavaPsiFacade.getElementFactory(callExpression.getProject()).createTypeFromText(returnType, callExpression);
        }
      }
      else {
        if (methodInfo.isReturnTypeCalculatorDefined()) {
          if (methodInfo.isApplicable(method)) {
            PsiType result = methodInfo.getReturnTypeCalculator().fun(callExpression, method);
            if (result != null) {
              return result;
            }
          }
        }
      }
    }

    return null;
  }
}
