/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableSearchUtils;
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
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
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
    final PsiTryStatement tryStatement = catchSection.getTryStatement();
    if (removeTryCatch) {
      final PsiCodeBlock codeBlock = tryStatement.getTryBlock();
      if (codeBlock == null) {
        return;
      }
      final PsiElement containingElement = tryStatement.getParent();
      final boolean keepBlock;
      if (containingElement instanceof PsiCodeBlock) {
        final PsiCodeBlock parentBlock = (PsiCodeBlock)containingElement;
        keepBlock = VariableSearchUtils.containsConflictingDeclarations(codeBlock, parentBlock);
      }
      else {
        keepBlock = true;
      }
      if (keepBlock) {
        tryStatement.replace(codeBlock);
      }
      else {
        final PsiElement firstBodyElement = codeBlock.getFirstBodyElement();
        final PsiElement lastBodyElement = codeBlock.getLastBodyElement();
        if (firstBodyElement != null && lastBodyElement != null) {
          containingElement.addRangeBefore(firstBodyElement, lastBodyElement, tryStatement);
        }
        tryStatement.delete();
      }
    }
    else {
      catchSection.delete();
    }
  }
}
