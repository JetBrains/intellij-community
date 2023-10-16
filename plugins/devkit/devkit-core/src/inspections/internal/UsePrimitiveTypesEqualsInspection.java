// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;

@VisibleForTesting
public final class UsePrimitiveTypesEqualsInspection extends UseEqualsInspectionBase {

  @Override
  protected @NotNull Class<?> getTargetClass() {
    return PsiPrimitiveType.class;
  }

  @Override
  protected boolean isExcluded(@NotNull UExpression operand) {
    return super.isExcluded(operand) ||
           operand instanceof UReferenceExpression &&
           isNullPrimitivePsiType(((UReferenceExpression)operand).resolve());
  }

  private static boolean isNullPrimitivePsiType(@Nullable PsiElement psiElement) {
    return psiElement instanceof PsiField &&
           isNullPrimitivePsiType((PsiField)psiElement);
  }

  private static boolean isNullPrimitivePsiType(@NotNull PsiField psiField) {
    return "NULL".equals(psiField.getName()) &&
           psiField.hasModifierProperty(PsiModifier.STATIC) &&
           psiField.hasModifierProperty(PsiModifier.FINAL) &&
           isPsiType(psiField.getContainingClass());
  }

  private static boolean isPsiType(@Nullable PsiClass containingClass) {
    return containingClass != null &&
           PsiType.class.getName().equals(containingClass.getQualifiedName());
  }
}
