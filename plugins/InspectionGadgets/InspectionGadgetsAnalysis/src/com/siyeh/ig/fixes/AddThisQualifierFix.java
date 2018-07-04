/*
 * Copyright 2011-2018 Bas Leijdekkers
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
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddThisQualifierFix extends InspectionGadgetsFix {

  private AddThisQualifierFix() {}

  @Nullable
  public static AddThisQualifierFix buildFix(PsiExpression expressionToQualify, PsiMember memberAccessed) {
    if (!isThisQualifierPossible(expressionToQualify, memberAccessed)) {
      return null;
    }
    return new AddThisQualifierFix();
  }

  private static boolean isThisQualifierPossible(PsiExpression memberAccessExpression, @NotNull PsiMember member) {
    final PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) {
      return false;
    }
    PsiClass containingClass = ClassUtils.getContainingClass(memberAccessExpression);
    if (InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
      // unqualified this.
      return true;
    }
    do {
      containingClass = ClassUtils.getContainingClass(containingClass);
    }
    while (containingClass != null && !InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true));
    // qualified this needed, which is not possible on local or anonymous class.
    return containingClass != null && !PsiUtil.isLocalOrAnonymousClass(containingClass);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("add.this.qualifier.quickfix");
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiReferenceExpression expression = (PsiReferenceExpression)descriptor.getPsiElement();
    if (expression.getQualifierExpression() != null) {
      return;
    }
    final PsiExpression thisQualifier = ExpressionUtils.getQualifierOrThis(expression);
    CommentTracker commentTracker = new CommentTracker();
    @NonNls final String newExpression = commentTracker.text(thisQualifier) + "." + commentTracker.text(expression);
    PsiReplacementUtil.replaceExpressionAndShorten(expression, newExpression, commentTracker);
  }
}
