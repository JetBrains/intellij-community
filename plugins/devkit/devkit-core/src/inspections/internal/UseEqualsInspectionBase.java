// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

public abstract class UseEqualsInspectionBase extends DevKitUastInspectionBase {

  @SuppressWarnings("unchecked")
  public static final Class<? extends UElement>[] HINTS = new Class[]{UBinaryExpression.class};

  @Override
  protected boolean isAllowed(@NotNull ProblemsHolder holder) {
    return super.isAllowed(holder) &&
           JavaPsiFacade.getInstance(holder.getProject())
             .findClass(getTargetClass().getName(), holder.getFile().getResolveScope()) != null;
  }

  protected abstract @NotNull Class<?> getTargetClass();

  @Override
  protected final @NotNull PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitBinaryExpression(@NotNull UBinaryExpression binaryExpression) {
        PsiElement sourcePsi = isEqualityExpression(binaryExpression) ? binaryExpression.getSourcePsi() : null;
        if (sourcePsi != null) {
          holder.registerProblem(sourcePsi, DevKitBundle.message("inspections.use.equals.description", getTargetClass().getSimpleName()));
        }
        return true;
      }
    }, HINTS);
  }

  private boolean isEqualityExpression(@NotNull UBinaryExpression binaryExpression) {
    UastBinaryOperator operator = binaryExpression.getOperator();
    if (operator != UastBinaryOperator.IDENTITY_EQUALS &&
        operator != UastBinaryOperator.IDENTITY_NOT_EQUALS) {
      return false;
    }

    UExpression leftOperand = binaryExpression.getLeftOperand();
    if (isExcluded(leftOperand)) {
      return false;
    }

    UExpression rightOperand = binaryExpression.getRightOperand();
    if (isExcluded(rightOperand)) {
      return false;
    }

    return hasTargetType(leftOperand) || hasTargetType(rightOperand);
  }

  protected boolean isExcluded(@NotNull UExpression operand) {
    return operand instanceof ULiteralExpression ||
           operand instanceof UThisExpression;
  }

  private boolean hasTargetType(@NotNull UExpression operand) {
    return operand.getSourcePsi() != null &&
           InheritanceUtil.isInheritor(operand.getExpressionType(), getTargetClass().getName());
  }
}
