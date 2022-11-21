// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.booleanIsAlwaysInverted;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("InspectionDescriptionNotFoundInspection") // via BooleanMethodIsAlwaysInvertedInspection
public class BooleanMethodIsAlwaysInvertedLocalInspection extends AbstractBaseJavaLocalInspectionTool {
  private final BooleanMethodIsAlwaysInvertedInspection myGlobalTool;

  BooleanMethodIsAlwaysInvertedLocalInspection(BooleanMethodIsAlwaysInvertedInspection globalTool) {
    myGlobalTool = globalTool;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return myGlobalTool.getGroupDisplayName();
  }

  @Override
  @NotNull
  public String getShortName() {
    return myGlobalTool.getShortName();
  }

  @Override
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!PsiType.BOOLEAN.equals(method.getReturnType()) || MethodUtils.hasSuper(method) || RefUtil.isImplicitRead(method)) return null;

    int[] usageCount = {0};
    if (!UnusedSymbolUtil.processUsages(manager.getProject(), method.getContainingFile(), method, new EmptyProgressIndicator(), null, u -> {
      PsiElement element = u.getElement();
      if (!(element instanceof PsiReferenceExpression)) return false;
      PsiMethodCallExpression methodCallExpression = ObjectUtils.tryCast(element.getParent(), PsiMethodCallExpression.class);
      if (methodCallExpression == null) return false;
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(methodCallExpression, PsiMethod.class, true, PsiMember.class);
      boolean isInverted = BooleanMethodIsAlwaysInvertedInspection.isInvertedMethodCall(methodCallExpression, containingMethod);
      if (isInverted) {
        usageCount[0]++;
        return true;
      } else {
        return false;
      }
    })) {
      return null;
    }
    if (usageCount[0] < 2) return null;
    final PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return null;
    return new ProblemDescriptor[] { myGlobalTool.createProblemDescriptor(manager, identifier, isOnTheFly) };
  }
}
