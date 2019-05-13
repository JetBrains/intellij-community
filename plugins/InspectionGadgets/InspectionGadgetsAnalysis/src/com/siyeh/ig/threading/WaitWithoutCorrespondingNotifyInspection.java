/*
 * Copyright 2006-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class WaitWithoutCorrespondingNotifyInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "wait.without.corresponding.notify.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "wait.without.corresponding.notify.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WaitWithoutCorrespondingNotifyVisitor();
  }

  private static class WaitWithoutCorrespondingNotifyVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!ThreadingUtils.isWaitCall(expression)) {
        return;
      }

      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)qualifier).resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)referent;
      final PsiClass fieldClass = field.getContainingClass();
      if (fieldClass == null) {
        return;
      }
      if (!PsiTreeUtil.isAncestor(fieldClass, expression, true)) {
        return;
      }
      if (containsNotifyCall(fieldClass, field)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean containsNotifyCall(
      PsiClass fieldClass, PsiField field) {
      final ContainsNotifyVisitor visitor =
        new ContainsNotifyVisitor(field);
      fieldClass.accept(visitor);
      return visitor.containsNotify();
    }
  }

  private static class ContainsNotifyVisitor
    extends JavaRecursiveElementWalkingVisitor {

    private final PsiField target;
    private boolean containsNotify;

    ContainsNotifyVisitor(PsiField target) {
      this.target = target;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (containsNotify) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!ThreadingUtils.isNotifyOrNotifyAllCall(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)qualifier).resolve();
      if (referent == null) {
        return;
      }
      if (!target.equals(referent)) {
        return;
      }
      containsNotify = true;
    }

    boolean containsNotify() {
      return containsNotify;
    }
  }
}
