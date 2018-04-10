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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArrayCreationWithoutNewKeywordInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("array.creation.without.new.keyword.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayCreationExpressionVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length == 1 && infos[0] instanceof String) {
      return new ArrayCreationExpressionFix((String)infos[0]);
    }
    return null;
  }

  private static class ArrayCreationExpressionVisitor extends BaseInspectionVisitor {
    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);
      final PsiType type = expression.getType();
      if (!(type instanceof PsiArrayType)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiNewExpression)) {
        final String typeText = TypeConversionUtil.erasure(type).getCanonicalText();
        registerError(expression, typeText);
      }
    }
  }

  private static class ArrayCreationExpressionFix extends InspectionGadgetsFix {
    private final String myType;

    public ArrayCreationExpressionFix(String type) {
      myType = type;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("array.creation.without.new.keyword.quickfix", myType);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("array.creation.without.new.keyword.family.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiArrayInitializerExpression) {
        final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)element;
        final PsiType type = arrayInitializerExpression.getType();
        if (type != null) {
          PsiReplacementUtil.replaceExpression(arrayInitializerExpression, "new " +
                                                                           TypeConversionUtil.erasure(type).getCanonicalText() +
                                                                           arrayInitializerExpression.getText());
        }
      }
    }
  }
}
