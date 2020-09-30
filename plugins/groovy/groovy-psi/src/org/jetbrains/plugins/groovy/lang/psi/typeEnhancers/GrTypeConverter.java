// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula;
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

import java.util.Collection;

/**
 * @author peter
 */
public abstract class GrTypeConverter {

  public static final ExtensionPointName<GrTypeConverter> EP_NAME = ExtensionPointName.create("org.intellij.groovy.typeConverter");

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

  public boolean isApplicableTo(@NotNull Position position) {
    return position == Position.ASSIGNMENT || position == Position.RETURN_VALUE;
  }

  /**
   * Checks if {@code actualType} can be converted to {@code targetType} in given {@code position}.
   */
  @Nullable
  public abstract ConversionResult isConvertible(@NotNull PsiType targetType,
                                                 @NotNull PsiType actualType,
                                                 @NotNull Position position,
                                                 @NotNull GroovyPsiElement context);

  @Nullable
  public Collection<ConstraintFormula> reduceTypeConstraint(@NotNull PsiType leftType,
                                                            @NotNull PsiType rightType,
                                                            @NotNull Position position,
                                                            @NotNull PsiElement context) {
    return null;
  }


  public enum Position {
    EXPLICIT_CAST,
    ASSIGNMENT,
    METHOD_PARAMETER,
    GENERIC_PARAMETER,
    RETURN_VALUE,
  }
}
