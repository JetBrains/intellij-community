package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyMethodInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Sergey Evdokimov
 */
public class GrDescriptorReturnTypeCalculator extends GrCallExpressionTypeCalculator {

  private static final RecursionGuard ourGuard = RecursionManager.createGuard("GrDescriptorReturnTypeCalculator getClosureReturnType");

  @Override
  public PsiType calculateReturnType(@NotNull GrMethodCall callExpression, @NotNull PsiMethod method) {
    for (GroovyMethodInfo methodInfo : GroovyMethodInfo.getInfos(method)) {
      String returnType = methodInfo.getReturnType();
      if (returnType != null) {
        if (methodInfo.isApplicable(method)) {
          return typeFromText(returnType, callExpression);
        }
      }
      else {
        if (methodInfo.getReturnTypeCalculatorClassName() != null && methodInfo.isApplicable(method)) {
          PsiType result = methodInfo.getReturnTypeCalculator().fun(callExpression, method);
          if (result != null) {
            return result;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  public static PsiType typeFromText(String typeName, GrMethodCall callExpression) {
    if (typeName.equals("!closure")) {
      GrExpression[] allArguments = PsiUtil.getAllArguments(callExpression);
      GrClosableBlock closure = null;

      for (GrExpression argument : allArguments) {
        if (argument instanceof GrClosableBlock) {
          closure = (GrClosableBlock)argument;
          break;
        }
      }

      if (closure == null) return null;

      final GrClosableBlock finalClosure = closure;

      return ourGuard.doPreventingRecursion(callExpression, true, new NullableComputable<PsiType>() {
        @Override
        public PsiType compute() {
          PsiType returnType = finalClosure.getReturnType();
          if (returnType == PsiType.VOID) return null;
          return returnType;
        }
      });
    }

    return JavaPsiFacade.getElementFactory(callExpression.getProject()).createTypeFromText(typeName, callExpression);
  }
}
