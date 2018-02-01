/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class DeleteCatchSectionFix extends InspectionGadgetsFix {

  private final boolean removeTryCatch;

  public DeleteCatchSectionFix(boolean removeTryCatch) {
    this.removeTryCatch = removeTryCatch;
  }

  @Override
  @NotNull
  public String getName() {
    if (removeTryCatch) {
      return InspectionGadgetsBundle.message("remove.try.catch.quickfix");
    }
    else {
      return InspectionGadgetsBundle.message("delete.catch.section.quickfix");
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Delete catch statement";
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiParameter)) {
      return;
    }
    final PsiParameter parameter = (PsiParameter)parent;
    final PsiElement grandParent = parameter.getParent();
    if (!(grandParent instanceof PsiCatchSection)) {
      return;
    }
    final PsiCatchSection catchSection = (PsiCatchSection)grandParent;
    if (removeTryCatch) {
      BlockUtils.unwrapTryBlock(catchSection.getTryStatement());
    }
    else {
      catchSection.delete();
    }
  }
}
