// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ExtractParameterAsLocalVariableFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class AssignmentToMethodParameterInspection extends BaseAssignmentToParameterInspection {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ExtractParameterAsLocalVariableFix();
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.to.method.parameter.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "assignment.to.method.parameter.ignore.transformation.option"), this, "ignoreTransformationOfOriginalParameter");
  }

  @Override
  protected boolean isApplicable(PsiParameter parameter) {
    final PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) {
      return false;
    }
    return !JavaPsiRecordUtil.isCompactConstructor((PsiMethod)scope);
  }
}
