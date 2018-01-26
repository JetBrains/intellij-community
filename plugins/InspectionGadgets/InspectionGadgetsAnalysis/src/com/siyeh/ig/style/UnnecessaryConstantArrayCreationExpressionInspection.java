/*
 * Copyright 2008-2018 Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryConstantArrayCreationExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.constant.array.creation.expression.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.constant.array.creation.expression.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length != 0 && infos[0] instanceof String) {
      return new UnnecessaryConstantArrayCreationExpressionFix((String)infos[0]);
    }
    return null;
  }

  private static class UnnecessaryConstantArrayCreationExpressionFix
    extends InspectionGadgetsFix {
    private final String myType;

    private UnnecessaryConstantArrayCreationExpressionFix(String type) {
      myType = type;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.constant.array.creation.expression.family.quickfix");
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.constant.array.creation.expression.quickfix", myType);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiNewExpression)) {
        return;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)element;
      final PsiArrayInitializerExpression arrayInitializer =
        newExpression.getArrayInitializer();
      if (arrayInitializer == null) {
        return;
      }
      new CommentTracker().replaceAndRestoreComments(newExpression, arrayInitializer);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryConstantArrayCreationExpressionVisitor();
  }

  private static class UnnecessaryConstantArrayCreationExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiNewExpression)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiVariable)) {
        return;
      }
      final PsiVariable variable = (PsiVariable)grandParent;
      final PsiType expressionType = expression.getType();
      if (!variable.getType().equals(expressionType)) {
        return;
      }
      if (hasGenericTypeParameters(variable)) {
        return;
      }
      registerError(parent, expressionType.getPresentableText());
    }

    private static boolean hasGenericTypeParameters(PsiVariable variable) {
      final PsiType type = variable.getType();
      final PsiType componentType = type.getDeepComponentType();
      if (!(componentType instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)componentType;
      final PsiType[] parameterTypes = classType.getParameters();
      for (PsiType parameterType : parameterTypes) {
        if (parameterType != null) {
          return true;
        }
      }
      return false;
    }
  }
}