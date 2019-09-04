// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author peter
 */
public abstract class GrTypeConverter {

  public static final ExtensionPointName<GrTypeConverter> EP_NAME = ExtensionPointName.create("org.intellij.groovy.typeConverter");

  protected static boolean isMethodCallConversion(GroovyPsiElement context) {
    return PsiUtil.isInMethodCallContext(context);
  }

  @Nullable
  protected static GrLiteral getLiteral(@NotNull GroovyPsiElement context) {
    final GrExpression expression;
    if (context instanceof GrTypeCastExpression) {
      expression = ((GrTypeCastExpression)context).getOperand();
    }
    else if (context instanceof GrAssignmentExpression) {
      expression = ((GrAssignmentExpression)context).getRValue();
    }
    else if (context instanceof GrVariable) {
      expression = ((GrVariable)context).getInitializerGroovy();
    }
    else if (context instanceof GrReturnStatement) {
      expression = ((GrReturnStatement)context).getReturnValue();
    }
    else {
      expression = context instanceof GrExpression ? (GrExpression)context : null;
    }
    return expression instanceof GrLiteral ? (GrLiteral)expression : null;
  }

  public boolean isApplicableTo(@NotNull ApplicableTo position) {
    return position == ApplicableTo.ASSIGNMENT || position == ApplicableTo.RETURN_VALUE;
  }

  /**
   * @deprecated see {@link #isConvertibleEx(com.intellij.psi.PsiType, com.intellij.psi.PsiType, org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement, org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.ApplicableTo)}
   */
  @Deprecated
  @Nullable
  public Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context) {
    return null;
  }

  /**
   * Checks if {@code actualType} can be converted to {@code targetType}.
   *
   * @param targetType target type
   * @param actualType actual type
   * @param context    context
   * @return {@link ConversionResult conversion result }
   */
  @Nullable
  public ConversionResult isConvertibleEx(@NotNull PsiType targetType,
                                          @NotNull PsiType actualType,
                                          @NotNull GroovyPsiElement context,
                                          @NotNull ApplicableTo currentPosition) {
    //noinspection deprecation
    final Boolean result = isConvertible(targetType, actualType, context);
    return result == null ? null
                          : result ? ConversionResult.OK
                                   : ConversionResult.ERROR;
  }

  public enum ApplicableTo {
    EXPLICIT_CAST,
    ASSIGNMENT,
    METHOD_PARAMETER,
    GENERIC_PARAMETER,
    RETURN_VALUE
  }
}
