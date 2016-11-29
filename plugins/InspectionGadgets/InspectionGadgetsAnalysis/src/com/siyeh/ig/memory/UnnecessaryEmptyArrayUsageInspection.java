/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.memory;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class UnnecessaryEmptyArrayUsageInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("constant.for.zero.length.array.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("constant.for.zero.length.array.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceEmptyArrayToConstantFix((PsiClass)infos[0], (PsiField)infos[1]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        if (ExpressionUtils.isZeroLengthArrayConstruction(expression)) {
          PsiType type = expression.getType();
          if (type instanceof PsiArrayType) {
            PsiType arrayType = ((PsiArrayType)type).getComponentType();
            PsiClass typeClass = PsiTypesUtil.getPsiClass(arrayType);
            if (typeClass != null) {
              for (PsiField field : typeClass.getFields()) {
                PsiModifierList modifiers = field.getModifierList();
                if (modifiers != null
                    && !typeClass.isEquivalentTo(PsiTreeUtil.findFirstParent(expression, (e) -> e instanceof PsiClass))
                    && modifiers.hasModifierProperty(PsiModifier.FINAL)
                    && modifiers.hasModifierProperty(PsiModifier.PUBLIC)
                    && ExpressionUtils.isZeroLengthArrayConstruction(field.getInitializer())) {
                  registerError(expression, typeClass, field);
                  return;
                }
              }
            }
          }
        }
        super.visitNewExpression(expression);
      }
    };
  }
}
