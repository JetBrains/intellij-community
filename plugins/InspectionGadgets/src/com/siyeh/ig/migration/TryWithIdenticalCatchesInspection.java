/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author yole
 */
public class TryWithIdenticalCatchesInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("try.with.identical.catches.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TryWithIdenticalCatchesVisitor();
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("try.with.identical.catches.display.name");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new CollapseCatchSectionsFix((Integer) infos[0]);
  }

  private static class TryWithIdenticalCatchesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitTryStatement(PsiTryStatement statement) {
      super.visitTryStatement(statement);
      if (!PsiUtil.isLanguageLevel7OrHigher(statement)) {
        return;
      }
      PsiCatchSection[] catchSections = statement.getCatchSections();
      boolean[] duplicates = new boolean[catchSections.length];
      for (int i = 0; i < catchSections.length; i++) {
        if (duplicates[i]) continue;
        final PsiCatchSection catchSection = catchSections[i];
        final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
        if (catchBlock == null) continue;
        final PsiParameter parameter = catchSection.getParameter();
        if (parameter == null) continue;
        InputVariables inputVariables = new InputVariables(Collections.singletonList(parameter),
                                                           statement.getProject(),
                                                           new LocalSearchScope(catchBlock),
                                                           false);
        DuplicatesFinder finder = new DuplicatesFinder(new PsiElement[] {catchBlock},
                                                       inputVariables, null, Collections.<PsiVariable>emptyList());
        for (int j = 0; j < catchSections.length; j++) {
          if (i == j || duplicates[j]) continue;
          final PsiCatchSection otherSection = catchSections[j];
          final PsiCodeBlock otherCatchBlock = otherSection.getCatchBlock();
          if (otherCatchBlock == null) continue;
          Match match = finder.isDuplicate(otherCatchBlock, true);
          if (match != null && match.getReturnValue() == null) {
            PsiJavaToken rParenth = otherSection.getRParenth();
            if (rParenth != null) {
                registerError(otherSection, 0, rParenth.getStartOffsetInParent() + 1, i);
            }
            duplicates[i] = true;
            duplicates[j] = true;
          }
        }
      }
    }
  }

  private static class CollapseCatchSectionsFix extends InspectionGadgetsFix {
    private final int myCollapseIntoIndex;

    public CollapseCatchSectionsFix(int collapseIntoIndex) {
      myCollapseIntoIndex = collapseIntoIndex;
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiCatchSection section = (PsiCatchSection) descriptor.getPsiElement();
      PsiTryStatement stmt = (PsiTryStatement) section.getParent();
      PsiCatchSection[] catchSections = stmt.getCatchSections();
      if (myCollapseIntoIndex >= catchSections.length) {
        return;   // something has gone stale
      }
      PsiCatchSection collapseInto = catchSections[myCollapseIntoIndex];
      PsiParameter parameter1 = collapseInto.getParameter();
      PsiParameter parameter2 = section.getParameter();
      if (parameter1 == null || parameter2 == null) {
        return;
      }
      @NonNls String text = "try { } catch(" + parameter1.getTypeElement().getText() + " | " + parameter2.getTypeElement().getText() + " e) { }";
      PsiTryStatement newTryCatch = (PsiTryStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(text, stmt);
      parameter1.getTypeElement().replace(newTryCatch.getCatchSections() [0].getParameter().getTypeElement());
      section.delete();
    }

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("try.with.identical.catches.quickfix");
    }
  }
}
