/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.threading;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;

abstract class AbstractReplaceWithAnotherMethodCallFix extends InspectionGadgetsFix {
  protected abstract String getMethodName();

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement methodNameElement = descriptor.getPsiElement();
    final PsiReferenceExpression methodExpression = (PsiReferenceExpression)methodNameElement.getParent();
    assert methodExpression != null;
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    CommentTracker commentTracker = new CommentTracker();
    if (qualifier == null) {
      PsiReplacementUtil.replaceExpression(methodExpression, getMethodName(), commentTracker);
    }
    else {
      final String qualifierText = commentTracker.text(qualifier);
      PsiReplacementUtil.replaceExpression(methodExpression, qualifierText + '.' + getMethodName(), commentTracker);
    }
  }
}