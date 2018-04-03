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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryQualifierForThisInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.qualifier.for.this.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(infos[0] instanceof PsiThisExpression
                                           ? "unnecessary.qualifier.for.this.problem.descriptor"
                                           : "unnecessary.qualifier.for.super.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryQualifierForThisVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryQualifierForThisFix();
  }

  private static class UnnecessaryQualifierForThisFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.qualifier.for.this.remove.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement qualifier = descriptor.getPsiElement();
      final PsiElement parent = qualifier.getParent();
      CommentTracker tracker = new CommentTracker();
      if (parent instanceof PsiThisExpression) {
        PsiReplacementUtil.replaceExpression((PsiThisExpression)parent, PsiKeyword.THIS, tracker);
      }
      else if (parent instanceof PsiSuperExpression) {
        PsiReplacementUtil.replaceExpression((PsiSuperExpression)parent, PsiKeyword.SUPER, tracker);
      }
    }
  }

  private static class UnnecessaryQualifierForThisVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThisExpression(@NotNull PsiThisExpression thisExpression) {
      super.visitThisExpression(thisExpression);
      final PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
      if (qualifier == null) {
        return;
      }
      final PsiElement referent = qualifier.resolve();
      if (!(referent instanceof PsiClass)) {
        return;
      }
      final PsiClass containingClass = ClassUtils.getContainingClass(thisExpression);
      if (containingClass == null || !containingClass.equals(referent)) {
        return;
      }
      registerError(qualifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL, thisExpression);
    }

    @Override
    public void visitSuperExpression(PsiSuperExpression expression) {
      super.visitSuperExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier == null) {
        return;
      }

      final PsiElement resolve = qualifier.resolve();
      if (!(resolve instanceof PsiClass)) {
        return;
      }

      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiReferenceExpression) {
        final PsiReferenceExpression copy;
        final PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiMethodCallExpression) {
          copy = ((PsiMethodCallExpression)gParent.copy()).getMethodExpression();
        }
        else {
          copy = (PsiReferenceExpression)parent.copy();
        }
        final PsiExpression copyQualifierExpression = copy.getQualifierExpression();
        assert copyQualifierExpression != null;
        PsiReplacementUtil.replaceExpression(copyQualifierExpression, PsiKeyword.SUPER);
        if (copy.resolve() == ((PsiReferenceExpression)parent).resolve()) {
          registerError(qualifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL, expression);
        }
      }
    }
  }
}