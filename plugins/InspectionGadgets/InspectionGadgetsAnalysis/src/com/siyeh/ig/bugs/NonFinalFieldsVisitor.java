/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

class NonFinalFieldsVisitor extends BaseInspectionVisitor {
  void checkUsedNonFinalFields(PsiMethod method) {
    method.accept(new JavaRecursiveElementWalkingVisitor() {

      @Override
      public void visitClass(PsiClass aClass) {
        // Do not recurse into.
      }

      @Override
      public void visitReferenceExpression(
        @NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (!(element instanceof PsiField)) {
          return;
        }
        final PsiField field = (PsiField)element;
        if (field.hasModifierProperty(PsiModifier.FINAL)) {
          return;
        }
        registerError(expression, field);
      }
    });
  }
}
