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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyPublicFieldAccessedInSynchronizedContextInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return "Non-private field accessed in synchronized context";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return "Non-private field <code>#ref</code> accessed in synchronized context  #loc";
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicFieldAccessedInSynchronizedContextVisitor();
  }

  private static class PublicFieldAccessedInSynchronizedContextVisitor
      extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull GrReferenceExpression expression) {
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField) element;
      if (field.hasModifierProperty(PsiModifier.PRIVATE) ||
          field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (!isInSynchronizedContext(expression)) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      registerError(expression);
    }

    private static boolean isInSynchronizedContext(PsiElement element) {
      final PsiElement context =
          PsiTreeUtil.getParentOfType(element, GrMethod.class,
              GrSynchronizedStatement.class);
      if (context instanceof GrSynchronizedStatement) {
        return true;
      }
      if (context != null) {
        final PsiModifierListOwner modifierListOwner =
            (PsiModifierListOwner) context;
        if (modifierListOwner.hasModifierProperty(
            PsiModifier.SYNCHRONIZED)) {
          return true;
        }
      }
      return false;
    }
  }
}
