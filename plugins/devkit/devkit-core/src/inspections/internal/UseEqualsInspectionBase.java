// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiType;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

public abstract class UseEqualsInspectionBase extends DevKitUastInspectionBase {

  @Override
  protected boolean isAllowed(@NotNull ProblemsHolder holder) {
    return super.isAllowed(holder) &&
           JavaPsiFacade.getInstance(holder.getProject())
             .findClass(getTargetClass().getName(), holder.getFile().getResolveScope()) != null;
  }

  @Override
  protected final @NotNull PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder,
                                                                  boolean isOnTheFly) {
    //noinspection unchecked
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(),
                                           new AbstractUastNonRecursiveVisitor() {

                                             @Override
                                             public boolean visitBinaryExpression(@NotNull UBinaryExpression binaryExpression) {
                                               PsiElement sourcePsi = isEqualityExpression(binaryExpression) ?
                                                                      binaryExpression.getSourcePsi() :
                                                                      null;
                                               if (sourcePsi != null) {
                                                 holder.registerProblem(sourcePsi,
                                                                        DevKitBundle.message("inspections.use.equals.description",
                                                                                             getTargetClassDescription()));
                                               }

                                               return true;
                                             }
                                           },
                                           new Class[]{UBinaryExpression.class});
  }

  protected abstract @NotNull Class<?> getTargetClass();

  protected @NotNull String getTargetClassDescription() {
    return getTargetClass().getSimpleName();
  }

  protected boolean isThisOrLiteral(@NotNull UExpression operand) {
    return operand instanceof ULiteralExpression ||
           operand instanceof UThisExpression;
  }

  private boolean hasTargetType(@NotNull UExpression operand) {
    PsiElement sourcePsi = operand.getSourcePsi();
    if (sourcePsi == null) {
      return false;
    }

    PsiType expressionType = operand.getExpressionType();
    return expressionType != null &&
           hasTargetType(sourcePsi, expressionType);
  }

  protected boolean hasTargetType(@NotNull PsiElement sourcePsi,
                                  @NotNull PsiType expressionType) {
    return PsiType.getTypeByName(getTargetClass().getName(),
                                 sourcePsi.getProject(),
                                 sourcePsi.getResolveScope())
      .isAssignableFrom(expressionType);
  }

  private boolean isEqualityExpression(@NotNull UBinaryExpression binaryExpression) {
    UastBinaryOperator operator = binaryExpression.getOperator();
    if (operator != UastBinaryOperator.IDENTITY_EQUALS &&
        operator != UastBinaryOperator.IDENTITY_NOT_EQUALS) {
      return false;
    }

    UExpression leftOperand = binaryExpression.getLeftOperand();
    if (isThisOrLiteral(leftOperand)) {
      return false;
    }

    UExpression rightOperand = binaryExpression.getRightOperand();
    if (isThisOrLiteral(rightOperand)) {
      return false;
    }

    return hasTargetType(leftOperand) || hasTargetType(rightOperand);
  }
}
