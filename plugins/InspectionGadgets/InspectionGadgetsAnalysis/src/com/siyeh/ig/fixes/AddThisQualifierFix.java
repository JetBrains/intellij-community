/*
 * Copyright 2011 Bas Leijdekkers
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AddThisQualifierFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("add.this.qualifier.quickfix");
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiReferenceExpression expression = (PsiReferenceExpression)descriptor.getPsiElement();
    if (expression.getQualifierExpression() != null) {
      return;
    }
    final PsiElement target = expression.resolve();
    if (!(target instanceof PsiMember)) {
      return;
    }
    final PsiMember member = (PsiMember)target;
    final PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) {
      return;
    }
    PsiClass containingClass = ClassUtils.getContainingClass(expression);
    @NonNls final String newExpression;
    if (InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
      newExpression = "this." + expression.getText();
    }
    else {
      containingClass = ClassUtils.getContainingClass(containingClass);
      if (containingClass == null) {
        return;
      }
      while (!InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
        containingClass = ClassUtils.getContainingClass(containingClass);
        if (containingClass == null) {
          return;
        }
      }
      newExpression = containingClass.getQualifiedName() + ".this." + expression.getText();
    }
    PsiReplacementUtil.replaceExpressionAndShorten(expression, newExpression);
  }
}
