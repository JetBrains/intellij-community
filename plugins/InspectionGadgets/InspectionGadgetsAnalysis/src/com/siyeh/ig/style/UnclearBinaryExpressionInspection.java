/*
 * Copyright 2011-2014 Bas Leijdekkers
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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnclearBinaryExpressionInspection extends BaseInspection {

  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "UnclearExpression";
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unclear.binary.expression.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unclear.binary.expression.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnclearBinaryExpressionFix();
  }

  private static class UnclearBinaryExpressionFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("unclear.binary.expression.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      replaceElement(descriptor.getPsiElement());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnclearBinaryExpressionVisitor();
  }

  private static class UnclearBinaryExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitExpression(PsiExpression expression) {
      super.visitExpression(expression);
      final PsiElement parent = expression.getParent();
      if (mightBeConfusingExpression(parent) || !isUnclearExpression(expression, parent)) {
        return;
      }
      registerError(expression);
    }
  }

  public static boolean mightBeConfusingExpression(@Nullable PsiElement element) {
    return (element instanceof PsiPolyadicExpression) || (element instanceof PsiConditionalExpression) ||
           (element instanceof PsiInstanceOfExpression) || (element instanceof PsiAssignmentExpression) ||
           (element instanceof PsiParenthesizedExpression);
  }

  @Contract("null, _ -> false")
  public static boolean isUnclearExpression(PsiExpression expression, PsiElement parent) {
    if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final IElementType tokenType = assignmentExpression.getOperationTokenType();
      if (parent instanceof PsiVariable) {
        if (!tokenType.equals(JavaTokenType.EQ)) {
          return true;
        }
      }
      else if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression nestedAssignment = (PsiAssignmentExpression)parent;
        final IElementType nestedTokenType = nestedAssignment.getOperationTokenType();
        if (!tokenType.equals(nestedTokenType)) {
          return true;
        }
      }
      return isUnclearExpression(assignmentExpression.getRExpression(), assignmentExpression);
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      if (PsiUtilCore.hasErrorElementChild(expression)) {
        return false;
      }
      return (parent instanceof PsiConditionalExpression) ||
             isUnclearExpression(conditionalExpression.getCondition(), conditionalExpression) ||
             isUnclearExpression(conditionalExpression.getThenExpression(), conditionalExpression) ||
             isUnclearExpression(conditionalExpression.getElseExpression(), conditionalExpression);
    }
    else if (expression instanceof PsiPolyadicExpression) {
      if ((parent instanceof PsiConditionalExpression) || (parent instanceof PsiPolyadicExpression) ||
          (parent instanceof PsiInstanceOfExpression)) {
        return true;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        final PsiType type = operand.getType();
        if ((type == null) || type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          return false;
        }
        if (isUnclearExpression(operand, polyadicExpression)) {
          return true;
        }
      }
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      if ((parent instanceof PsiPolyadicExpression) || (parent instanceof PsiConditionalExpression)) {
        return true;
      }
      final PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
      return isUnclearExpression(instanceOfExpression.getOperand(), instanceOfExpression);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      final PsiExpression nestedExpression = parenthesizedExpression.getExpression();
      return isUnclearExpression(nestedExpression, parenthesizedExpression);
    }
    return false;
  }

  public static void replaceElement(PsiElement element) {
    if (!(element instanceof PsiExpression)) {
      return;
    }
    final PsiExpression expression = (PsiExpression)element;
    final String newExpressionText = createReplacementText(expression, new StringBuilder()).toString();
    PsiReplacementUtil.replaceExpression(expression, newExpressionText);
  }

  private static StringBuilder createReplacementText(@Nullable PsiExpression expression, StringBuilder out) {
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final PsiElement parent = expression.getParent();
      final boolean parentheses = (parent instanceof PsiConditionalExpression) ||
                                  (parent instanceof PsiInstanceOfExpression) ||
                                  (parent instanceof PsiPolyadicExpression);
      appendText(polyadicExpression, parentheses, out);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      for (PsiElement child : expression.getChildren()) {
        if (child instanceof PsiExpression) {
          final PsiExpression unwrappedExpression = (PsiExpression)child;
          createReplacementText(unwrappedExpression, out);
        }
        else {
          out.append(child.getText());
        }
      }
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      final PsiElement parent = expression.getParent();
      final boolean parentheses = (parent instanceof PsiPolyadicExpression) || (parent instanceof PsiConditionalExpression);
      appendText(expression, parentheses, out);
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiElement parent = expression.getParent();
      final boolean parentheses = (parent instanceof PsiConditionalExpression) || (parent instanceof PsiPolyadicExpression) ||
                                  (parent instanceof PsiInstanceOfExpression);
      appendText(expression, parentheses, out);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final PsiElement parent = expression.getParent();
      final boolean parentheses = !isSimpleAssignment(assignmentExpression, parent);
      appendText(assignmentExpression, parentheses, out);
    }
    else if (expression != null) {
      out.append(expression.getText());
    }
    return out;
  }

  private static boolean isSimpleAssignment(PsiAssignmentExpression assignmentExpression, PsiElement parent) {
    final IElementType parentTokenType;
    if (parent instanceof PsiExpressionStatement) {
      return true;
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression parentAssignmentExpression = (PsiAssignmentExpression)parent;
      parentTokenType = parentAssignmentExpression.getOperationTokenType();
    }
    else if (parent instanceof PsiVariable) {
      parentTokenType = JavaTokenType.EQ;
    }
    else {
      return false;
    }
    final IElementType tokenType = assignmentExpression.getOperationTokenType();
    return parentTokenType.equals(tokenType);
  }

  private static void appendText(PsiExpression expression, boolean parentheses, StringBuilder out) {
    if (parentheses) {
      out.append('(');
    }
    for (PsiElement child : expression.getChildren()) {
      if (child instanceof PsiExpression) {
        createReplacementText((PsiExpression)child, out);
      }
      else {
        out.append(child.getText());
      }
    }
    if (parentheses) {
      out.append(')');
    }
  }
}
