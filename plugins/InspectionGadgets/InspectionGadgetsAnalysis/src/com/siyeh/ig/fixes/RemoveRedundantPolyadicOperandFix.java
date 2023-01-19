// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

public class RemoveRedundantPolyadicOperandFix extends InspectionGadgetsFix {
  private final String myExpressionText;

  public RemoveRedundantPolyadicOperandFix(String expressionText) {
    myExpressionText = expressionText;
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("remove.redundant.polyadic.operand.fix.name", myExpressionText);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("remove.redundant.polyadic.operand.fix.family.name");
  }

  @Override
  public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiPolyadicExpression polyadicExpression = PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class);
    if (polyadicExpression == null) return;
    PsiElement[] children = polyadicExpression.getChildren();

    // We know that at least one operand is present after current, so we just remove everything till the next operand
    int start = IntStreamEx.ofIndices(children, child -> PsiTreeUtil.isAncestor(child, element, false)).findFirst().orElse(-1);
    if (start == -1) return;
    int end = IntStreamEx.range(start + 1, children.length).findFirst(idx -> children[idx] instanceof PsiExpression).orElse(-1);
    if (end == -1) return;
    CommentTracker ct = new CommentTracker();
    String replacement = IntStreamEx.range(0, start).append(IntStreamEx.range(end, children.length)).elements(children)
      .map(ct::text).joining();
    ct.replaceAndRestoreComments(polyadicExpression, replacement);
  }
}
