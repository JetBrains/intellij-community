/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.cloneable;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class CloneCallsConstructorsInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("clone.instantiates.objects.with.constructor.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    return type instanceof PsiArrayType
           ? InspectionGadgetsBundle.message("clone.instantiates.new.array.problem.descriptor", type.getPresentableText())
           : InspectionGadgetsBundle.message("clone.instantiates.objects.with.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneCallsConstructorVisitor();
  }

  private static class CloneCallsConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!CloneUtils.isClone(method) || method.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || aClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      method.accept(new JavaRecursiveElementWalkingVisitor() {

        @Override
        public void visitNewExpression(@NotNull PsiNewExpression newExpression) {
          super.visitNewExpression(newExpression);
          if (newExpression.getAnonymousClass() != null) {
            return;
          }
          if (ParenthesesUtils.getParentSkipParentheses(newExpression) instanceof PsiThrowStatement) {
            return;
          }
          final PsiType type = newExpression.getType();
          if (type instanceof PsiClassType) {
            final PsiClass clonedClass = ((PsiClassType) type).resolve();
            if (clonedClass != aClass && !InheritanceUtil.isInheritor(clonedClass, CommonClassNames.JAVA_LANG_CLONEABLE)) {
              return;
            }
          }
          registerNewExpressionError(newExpression, type);
        }
      });
    }
  }
}