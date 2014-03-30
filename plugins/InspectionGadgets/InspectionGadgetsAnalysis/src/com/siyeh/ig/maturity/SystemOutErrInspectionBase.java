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
package com.siyeh.ig.maturity;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class SystemOutErrInspectionBase extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "UseOfSystemOutOrSystemErr";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "use.system.out.err.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "use.system.out.err.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SystemOutErrVisitor();
  }

  private static class SystemOutErrVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final String name = expression.getReferenceName();
      if (!HardcodedMethodConstants.OUT.equals(name) &&
          !HardcodedMethodConstants.ERR.equals(name)) {
        return;
      }
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)referent;
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (!"java.lang.System".equals(className)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}