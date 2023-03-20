/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PropertyUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InlineGetterSetterCallFix extends InspectionGadgetsFix {
  private final boolean myGetter;

  public InlineGetterSetterCallFix(boolean getter) {
    myGetter = getter;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getName() {
    return myGetter
           ? InspectionGadgetsBundle.message("call.to.simple.getter.in.class.inline.quickfix")
           : InspectionGadgetsBundle.message("call.to.simple.setter.in.class.inline.quickfix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("inline.call.quickfix");
  }

  @Override
  public void doFix(final @NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement nameElement = descriptor.getPsiElement();
    final PsiReferenceExpression methodExpression = (PsiReferenceExpression)nameElement.getParent();
    if (methodExpression == null) return;
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)methodExpression.getParent();
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) return;
    final PsiField field = myGetter ? PropertyUtil.getFieldOfGetter(method) : PropertyUtil.getFieldOfSetter(method);
    if (field == null) return;
    final String name = field.getName();
    final CommentTracker tracker = new CommentTracker();
    @NonNls final StringBuilder newText = new StringBuilder();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier != null) {
      newText.append(tracker.text(qualifier)).append('.');
    }
    else {
      final PsiVariable variable = JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedVariable(name, methodExpression);
      if (variable != field) newText.append("this.");
    }
    newText.append(name);
    if (!myGetter) {
      newText.append("=").append(methodCallExpression.getArgumentList().getExpressions()[0].getText());
    }
    tracker.replaceAndRestoreComments(methodCallExpression, newText.toString());
  }
}
