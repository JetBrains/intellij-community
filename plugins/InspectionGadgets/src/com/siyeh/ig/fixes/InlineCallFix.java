/*
 * Copyright 2003-2005 Dave Griffith
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class InlineCallFix extends InspectionGadgetsFix {
  private final String myName;

  public InlineCallFix(String name) {
    myName = name;
  }

  public InlineCallFix() {
    this(InspectionGadgetsBundle.message("inline.call.quickfix"));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myName;
  }

  @Override
  @NotNull
  public String getName() {
    return getFamilyName();
  }

  @Override
  public void doFix(final Project project, ProblemDescriptor descriptor) {
    final PsiElement nameElement = descriptor.getPsiElement();
    final PsiReferenceExpression methodExpression = (PsiReferenceExpression)nameElement.getParent();
    if (methodExpression == null) return;
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)methodExpression.getParent();
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) return;
    inline(project, methodExpression, method);
  }

  protected void inline(Project project, PsiReferenceExpression methodExpression, PsiMethod method) {
    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    final InlineMethodProcessor processor = new InlineMethodProcessor(project, method, methodExpression, null, true,
                                                                      settings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD,
                                                                      settings.RENAME_SEARCH_FOR_TEXT_FOR_METHOD);
    processor.inlineMethodCall(processor.addBracesWhenNeeded(new PsiReferenceExpression[]{methodExpression})[0]);
  }
}
