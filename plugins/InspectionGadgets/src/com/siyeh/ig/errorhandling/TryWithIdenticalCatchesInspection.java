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
package com.siyeh.ig.errorhandling;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author yole
 */
public class TryWithIdenticalCatchesInspection extends BaseInspection {
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
  public TextRange getProblemTextRange(PsiElement element) {
    if (element instanceof PsiCatchSection) {
      PsiJavaToken rParenth = ((PsiCatchSection)element).getRParenth();
      if (rParenth != null) {
        return new TextRange(0, rParenth.getTextOffset() + 1 - element.getTextOffset());
      }
    }
    return null;
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
        InputVariables inputVariables = new InputVariables(Collections.singletonList(catchSections[i].getParameter()),
                                                           statement.getProject(),
                                                           new LocalSearchScope(catchSections[i].getCatchBlock()),
                                                           false);
        DuplicatesFinder finder = new DuplicatesFinder(new PsiElement[] { catchSections [i].getCatchBlock() },
                                                       inputVariables, null, Collections.<PsiVariable>emptyList());
        for (int j = 0; j < catchSections.length; j++) {
          if (i == j || duplicates[j]) continue;
          Match match = finder.isDuplicate(catchSections[j].getCatchBlock(), true);
          if (match != null) {
            registerError(catchSections[j]);
            duplicates[i] = true;
            duplicates[j] = true;
          }
        }
      }
    }
  }
}
