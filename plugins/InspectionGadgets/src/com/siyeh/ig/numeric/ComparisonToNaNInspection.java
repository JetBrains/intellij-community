/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ComparisonToNaNInspection extends ExpressionInspection {

  private final ComparisonToNaNFix fix = new ComparisonToNaNFix();

  public String getGroupDisplayName() {
    return GroupNames.NUMERIC_GROUP_NAME;
  }

  public String buildErrorString(PsiElement location) {
    final PsiBinaryExpression comparison =
      (PsiBinaryExpression)location.getParent();
    assert comparison != null;
    final PsiJavaToken sign = comparison.getOperationSign();
    final IElementType tokenType = sign.getTokenType();
    if (tokenType.equals(JavaTokenType.EQEQ)) {
      return InspectionGadgetsBundle.message("comparison.to.na.n.problem.descriptor1");
    }
    else {
      return InspectionGadgetsBundle.message("comparison.to.na.n.problem.descriptor2");
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ComparisonToNaNVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class ComparisonToNaNFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("comparison.to.na.n.replace.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiReferenceExpression NaNExpression =
        (PsiReferenceExpression)descriptor.getPsiElement();
      final String typeString = NaNExpression.getQualifier().getText();
      final PsiBinaryExpression comparison =
        (PsiBinaryExpression)NaNExpression.getParent();

      final PsiExpression lhs = comparison.getLOperand();
      final PsiExpression rhs = comparison.getROperand();
      final PsiExpression qualifier;
      if (NaNExpression.equals(lhs)) {
        qualifier = rhs;
      }
      else {
        qualifier = lhs;
      }

      assert qualifier != null;
      final String qualifierText = qualifier.getText();
      final PsiJavaToken sign = comparison.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      final String negationString;
      if (tokenType.equals(JavaTokenType.EQEQ)) {
        negationString = "";
      }
      else {
        negationString = "!";
      }
      @NonNls final String newExpressionText = negationString + typeString +
                                       ".isNaN(" + qualifierText + ')';
      replaceExpression(comparison, newExpressionText);
    }
  }

  private static class ComparisonToNaNVisitor extends BaseInspectionVisitor {
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!(expression.getROperand() != null)) {
        return;
      }
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiExpression rhs = expression.getROperand();
      if (!isFloatingPointType(lhs) && !isFloatingPointType(rhs)) {
        return;
      }
      if (isNaN(lhs)) {
        registerError(lhs);
      }
      else if (isNaN(rhs)) {
        registerError(rhs);
      }
    }

    private static boolean isFloatingPointType(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return false;
      }
      return PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type);
    }

    private static boolean isNaN(PsiExpression expression) {
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)expression;
      @NonNls final String referenceName = referenceExpression.getReferenceName();
      if (!"NaN".equals(referenceName)) {
        return false;
      }
      final PsiElement qualifier = referenceExpression.getQualifier();
      if (qualifier == null) {
        return false;
      }
      @NonNls final String qualifierText = qualifier.getText();
      return "Double".equals(qualifierText) ||
             "Float" .equals(qualifierText);
    }
  }
}
