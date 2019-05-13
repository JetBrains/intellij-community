// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.InheritanceUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public abstract class InconvertibleTypesChecker {
  protected abstract void registerEqualsError(PsiElement highlightLocation,
                                              @NotNull PsiType leftType,
                                              @NotNull PsiType rightType,
                                              boolean convertible);

  public void checkTypes(@NotNull PsiReferenceExpression expression,
                         @NotNull PsiType leftType,
                         @NotNull PsiType rightType,
                         boolean warnIfNoMutualSubclassFound, 
                         boolean onTheFly) {
    PsiElement name = expression.getReferenceNameElement();
    if (name == null) {
      return;
    }
    if (TypeUtils.areConvertible(leftType, rightType) || TypeUtils.mayBeEqualByContract(leftType, rightType)) {
      deepCheck(leftType, rightType, name, new HashMap<>(), warnIfNoMutualSubclassFound, onTheFly);
      return;
    }
    registerEqualsError(name, leftType, rightType, false);
  }

  protected void deepCheck(@NotNull PsiType leftType,
                           @NotNull PsiType rightType,
                           PsiElement highlightLocation,
                           Map<PsiType, PsiType> checked,
                           boolean warnIfNoMutualSubclassFound,
                           boolean onTheFly) {
    PsiType checkedRight = checked.putIfAbsent(leftType, rightType);
    if (checkedRight != null) {
      if (!checkedRight.equals(rightType)) {
        registerEqualsError(highlightLocation, leftType, rightType, false);
      }
      return;
    }
    if (leftType instanceof PsiCapturedWildcardType) {
      leftType = ((PsiCapturedWildcardType)leftType).getUpperBound();
    }
    if (rightType instanceof PsiCapturedWildcardType) {
      rightType = ((PsiCapturedWildcardType)rightType).getUpperBound();
    }
    if (leftType.isAssignableFrom(rightType) || rightType.isAssignableFrom(leftType)) return;
    PsiClass leftClass = PsiUtil.resolveClassInClassTypeOnly(leftType);
    PsiClass rightClass = PsiUtil.resolveClassInClassTypeOnly(rightType);
    if (leftClass == null || rightClass == null) return;
    if (!rightClass.isInterface()) {
      PsiClass tmp = leftClass;
      leftClass = rightClass;
      rightClass = tmp;
    }
    if (leftClass == rightClass || TypeUtils.mayBeEqualByContract(leftType, rightType)) {
      // check type parameters
      if (leftType instanceof PsiClassType && rightType instanceof PsiClassType) {
        final PsiType[] leftParameters = ((PsiClassType)leftType).getParameters();
        final PsiType[] rightParameters = ((PsiClassType)rightType).getParameters();
        if (leftParameters.length == rightParameters.length) {
          for (int i = 0, length = leftParameters.length; i < length; i++) {
            final PsiType leftParameter = leftParameters[i];
            final PsiType rightParameter = rightParameters[i];
            if (!TypeUtils.areConvertible(leftParameter, rightParameter) &&
                !TypeUtils.mayBeEqualByContract(leftParameter, rightParameter)) {
              registerEqualsError(highlightLocation, leftType, rightType, false);
              return;
            }
            deepCheck(leftParameter, rightParameter, highlightLocation, checked, warnIfNoMutualSubclassFound, onTheFly);
          }
        }
      }
    }
    else if (TypeUtils.cannotBeEqualByContract(leftType, rightType)) {
      registerEqualsError(highlightLocation, leftType, rightType, false);
    }
    else if (warnIfNoMutualSubclassFound && !InheritanceUtil.existsMutualSubclass(leftClass, rightClass, onTheFly)) {
      registerEqualsError(highlightLocation, leftType, rightType, true);
    }
  }
}
