/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.util.InlineUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.HighlightUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class InlineVariableFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("inline.variable.quickfix");
  }

  @Override
  public void doFix(@NotNull final Project project, final ProblemDescriptor descriptor) {
    final PsiElement nameElement = descriptor.getPsiElement();
    final PsiLocalVariable variable = (PsiLocalVariable)nameElement.getParent();
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      return;
    }
    final Collection<PsiReference> references = ReferencesSearch.search(variable).findAll();
    final Collection<PsiElement> replacedElements = new ArrayList<>();
    for (PsiReference reference : references) {
      final PsiExpression expression = InlineUtil.inlineVariable(variable, initializer, (PsiJavaCodeReferenceElement)reference);
      replacedElements.add(expression);
    }
    if (isOnTheFly()) {
      HighlightUtils.highlightElements(replacedElements);
    }
    variable.delete();
  }
}