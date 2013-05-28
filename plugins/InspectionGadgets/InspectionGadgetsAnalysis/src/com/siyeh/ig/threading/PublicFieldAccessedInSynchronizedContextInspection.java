/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SynchronizationUtil;
import org.jetbrains.annotations.NotNull;

public class PublicFieldAccessedInSynchronizedContextInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("public.field.accessed.in.synchronized.context.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "NonPrivateFieldAccessedInSynchronizedContext";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("public.field.accessed.in.synchronized.context.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicFieldAccessedInSynchronizedContextVisitor();
  }

  private static class PublicFieldAccessedInSynchronizedContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
        return;
      }
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)element;
      if (field.hasModifierProperty(PsiModifier.PRIVATE) || field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (!SynchronizationUtil.isInSynchronizedContext(expression)) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null || containingClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      registerError(expression);
    }
  }
}