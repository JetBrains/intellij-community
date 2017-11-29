/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.threading;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyAccessToStaticFieldLockedOnInstanceInspection
    extends BaseInspection {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return "Access to static field locked on instance data";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return "Access to static field <code>#ref</code> locked on instance data #loc";
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor
      extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull GrReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      boolean isLockedOnInstance = false;
      boolean isLockedOnClass = false;
      final GrMethod containingMethod =
          PsiTreeUtil.getParentOfType(expression, GrMethod.class);
      if (containingMethod != null &&
          containingMethod.hasModifierProperty(
              PsiModifier.SYNCHRONIZED)) {
        if (containingMethod.hasModifierProperty(
            PsiModifier.STATIC)) {
          isLockedOnClass = true;
        } else {
          isLockedOnInstance = true;
        }
      }
      PsiElement elementToCheck = expression;
      while (true) {
        final GrSynchronizedStatement syncStatement = PsiTreeUtil.getParentOfType(elementToCheck, GrSynchronizedStatement.class);
        if (syncStatement == null) {
          break;
        }
        final GrExpression lockExpression = syncStatement.getMonitor();

        if (lockExpression instanceof GrReferenceExpression && PsiUtil.isThisReference(lockExpression)) {
          isLockedOnInstance = true;
        }
        else if (lockExpression instanceof GrReferenceExpression) {
          final GrReferenceExpression reference = (GrReferenceExpression) lockExpression;
          final PsiElement referent = reference.resolve();
          if (referent instanceof PsiField) {
            final PsiField referentField = (PsiField) referent;
            if (referentField.hasModifierProperty(PsiModifier.STATIC)) {
              isLockedOnClass = true;
            } else {
              isLockedOnInstance = true;
            }
          }
        }
        elementToCheck = syncStatement;
      }
      if (!isLockedOnInstance || isLockedOnClass) {
        return;
      }
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField referredField = (PsiField) referent;
      if (!referredField.hasModifierProperty(PsiModifier.STATIC) ||
          isConstant(referredField)) {
        return;
      }
      final PsiClass containingClass = referredField.getContainingClass();
      if (!PsiTreeUtil.isAncestor(containingClass, expression, false)) {
        return;
      }
      registerError(expression);
    }

    private static boolean isConstant(PsiField field) {
      return field.hasModifierProperty(PsiModifier.FINAL);
    }
  }
}
