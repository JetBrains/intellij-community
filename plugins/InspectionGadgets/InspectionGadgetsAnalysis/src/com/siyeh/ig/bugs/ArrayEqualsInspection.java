/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ArrayEqualsInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "equals.called.on.array.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "equals.called.on.array.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiArrayType type = (PsiArrayType)infos[0];
    if (type != null) {
      final PsiType componentType = type.getComponentType();
      if (componentType instanceof PsiArrayType) {
        return new ArrayEqualsFix(true);
      }
    }
    return new ArrayEqualsFix(false);
  }

  private static class ArrayEqualsFix extends InspectionGadgetsFix {

    private final boolean deepEquals;

    public ArrayEqualsFix(boolean deepEquals) {
      this.deepEquals = deepEquals;
    }

    @Override
    @NotNull
    public String getName() {
      if (deepEquals) {
        return InspectionGadgetsBundle.message(
          "replace.with.arrays.deep.equals");
      }
      else {
        return InspectionGadgetsBundle.message(
          "replace.with.arrays.equals");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with Arrays.equals";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor){
      final PsiIdentifier name = (PsiIdentifier)descriptor.getPsiElement();
      final PsiReferenceExpression expression = (PsiReferenceExpression)name.getParent();
      assert expression != null;
      final PsiMethodCallExpression call = (PsiMethodCallExpression)expression.getParent();
      final PsiExpression qualifier = expression.getQualifierExpression();
      assert qualifier != null;
      CommentTracker commentTracker = new CommentTracker();
      final String qualifierText = commentTracker.text(qualifier);
      assert call != null;
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final String argumentText = commentTracker.text(arguments[0]);
      @NonNls final StringBuilder newExpressionText = new StringBuilder();
      if (deepEquals) {
        newExpressionText.append("java.util.Arrays.deepEquals(");
      }
      else {
        newExpressionText.append("java.util.Arrays.equals(");
      }
      newExpressionText.append(qualifierText);
      newExpressionText.append(", ");
      newExpressionText.append(argumentText);
      newExpressionText.append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(call, newExpressionText.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayEqualsVisitor();
  }

  private static class ArrayEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      if (argument == null) {
        return;
      }
      final PsiType argumentType = argument.getType();
      if (!(argumentType instanceof PsiArrayType)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiType qualifierType = qualifier.getType();
      if (!(qualifierType instanceof PsiArrayType)) {
        return;
      }
      registerMethodCallError(expression, qualifierType);
    }
  }
}
