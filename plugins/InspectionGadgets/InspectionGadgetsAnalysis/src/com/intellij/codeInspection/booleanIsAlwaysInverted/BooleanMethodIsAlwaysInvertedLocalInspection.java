// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.booleanIsAlwaysInverted;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BooleanMethodIsAlwaysInvertedLocalInspection extends AbstractBaseJavaLocalInspectionTool {
  private final BooleanMethodIsAlwaysInvertedInspectionBase myGlobalTool;

  BooleanMethodIsAlwaysInvertedLocalInspection(BooleanMethodIsAlwaysInvertedInspectionBase globalTool) {
    myGlobalTool = globalTool;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return myGlobalTool.getGroupDisplayName();
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return myGlobalTool.getDisplayName();
  }

  @Override
  @NotNull
  public String getShortName() {
    return myGlobalTool.getShortName();
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    PsiType returnType = method.getReturnType();
    if (!PsiType.BOOLEAN.equals(returnType) ||
        MethodUtils.hasSuper(method) ||
        RefUtil.isImplicitRead(method)) return null;

    int[] usageCount = {0};
    if (!UnusedSymbolUtil.processUsages(manager.getProject(), method.getContainingFile(), method, new EmptyProgressIndicator(), null, u -> {
      PsiElement element = u.getElement();
      if (!(element instanceof PsiReferenceExpression)) return false;
      PsiMethodCallExpression methodCallExpression = ObjectUtils.tryCast(element.getParent(), PsiMethodCallExpression.class);
      if (methodCallExpression == null) return false;
      boolean isInverted = BooleanMethodIsAlwaysInvertedInspectionBase.isInvertedMethodCall(methodCallExpression);
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
    return new ProblemDescriptor[] { myGlobalTool.createProblemDescriptor(manager, method.getNameIdentifier(), isOnTheFly) };
  }
}
