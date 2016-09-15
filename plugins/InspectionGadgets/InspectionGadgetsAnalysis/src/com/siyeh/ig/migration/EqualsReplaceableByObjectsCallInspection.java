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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class EqualsReplaceableByObjectsCallInspection extends BaseInspection {
  public boolean checkNotNull;

  @NotNull
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("equals.replaceable.by.objects.check.not.null.option"), this, "checkNotNull");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new EqualsReplaceableByObjectsCallFix((String)infos[0], (String)infos[1], (Boolean)infos[2]);
  }

  private static class EqualsReplaceableByObjectsCallFix extends InspectionGadgetsFix {

    private final String myName1;
    private final String myName2;
    private final Boolean myEquals;

    public EqualsReplaceableByObjectsCallFix(String name1, String name2, Boolean equals) {
      myName1 = name1;
      myName2 = name2;
      myEquals = equals;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiBinaryExpression || element instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiExpression expression = (PsiExpression)element;
      final String expressionText = "java.util.Objects.equals(" + myName1 + "," + myName2 + ")";
      PsiReplacementUtil.replaceExpressionAndShorten(expression, myEquals ? expressionText : "!" + expressionText);
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel7OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsReplaceableByObjectsCallVisitor();
  }

  private class EqualsReplaceableByObjectsCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      final String methodName = expression.getMethodExpression().getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(methodName)) {
        return;
      }
      final PsiElement maybeBinary = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class, PsiPrefixExpression.class);
      if (maybeBinary instanceof PsiBinaryExpression) {
        if (processNotNullCheck((PsiBinaryExpression)maybeBinary)) {
          return;
        }
      }
      if (!checkNotNull) {
        final PsiExpression qualifierExpression = getQualifierExpression(expression);
        if (qualifierExpression == null) {
          return;
        }
        final PsiExpression argumentExpression = getArgumentExpression(expression);
        if (argumentExpression == null) {
          return;
        }
        registerError(expression, qualifierExpression.getText(), argumentExpression.getText(), true);
      }
    }

    private PsiExpression getQualifierExpression(PsiMethodCallExpression expression) {
      return ParenthesesUtils.stripParentheses(expression.getMethodExpression().getQualifierExpression());
    }

    private boolean processNotNullCheck(PsiBinaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      final PsiExpression rightOperand = ParenthesesUtils.stripParentheses(expression.getROperand());
      if (JavaTokenType.ANDAND.equals(tokenType)) {
        return registerProblem(expression, rightOperand, true);
      }
      else if (JavaTokenType.OROR.equals(tokenType)) {
        if (rightOperand instanceof PsiPrefixExpression &&
            JavaTokenType.EXCL.equals(((PsiPrefixExpression)rightOperand).getOperationTokenType())) {
          final PsiExpression negatedRightOperand = ParenthesesUtils.stripParentheses(((PsiPrefixExpression)rightOperand).getOperand());
          return registerProblem(expression, negatedRightOperand, false);
        }
      }
      return true;
    }

    /**
     * Match the patterns, and register the error if a pattern is matched:
     * <pre>
     * x==null || !x.equals(y)
     * x!=null && x.equals(y)</pre>
     *
     * @return true if the pattern is matched
     */
    private boolean registerProblem(PsiBinaryExpression expression, PsiExpression rightOperand, boolean equal) {
      if ((rightOperand instanceof PsiMethodCallExpression)) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)rightOperand;
        final PsiReferenceExpression nullCheckedExpression =
          ExpressionUtils.getReferenceExpressionFromNullComparison(expression.getLOperand(), !equal);
        final String nullCheckedName = getQualifiedVariableName(nullCheckedExpression);
        if (nullCheckedName != null) {
          final PsiExpression qualifierExpression = getQualifierExpression(methodCallExpression);
          final String qualifierName = getQualifiedVariableName(qualifierExpression);
          if (qualifierName != null && qualifierName.equals(nullCheckedName)) {
            final PsiExpression argumentExpression = getArgumentExpression(methodCallExpression);
            final String argumentName = getQualifiedVariableName(argumentExpression);
            final PsiExpression expressionToReplace =
              argumentName != null ? checkEqualityBefore(expression, equal, qualifierName, argumentName) : expression;
            registerError(expressionToReplace, nullCheckedExpression.getText(), argumentExpression.getText(), Boolean.valueOf(equal));
            return true;
          }
        }
      }
      return false;
    }

    /**
     * Match the left side of the patterns:
     * <pre>
     * x!=y && (x==null || !x.equals(y))
     * x==y || (x!=null && x.equals(y))</pre>
     *
     * @return the expression matching the pattern, or the original expression if there's no match
     */
    @Nullable
    private PsiExpression checkEqualityBefore(PsiExpression expression, boolean equal, String qualifiedName1, String qualifiedName2) {
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
      if (parent instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (equal && JavaTokenType.OROR.equals(tokenType) || !equal && JavaTokenType.ANDAND.equals(tokenType)) {
          if (PsiTreeUtil.isAncestor(binaryExpression.getROperand(), expression, false)) {
            final PsiExpression lhs = binaryExpression.getLOperand();
            if (isEquality(lhs, equal, qualifiedName1, qualifiedName2)) {
              return binaryExpression;
            }
          }
        }
      }
      return expression;
    }

    private boolean isEquality(PsiExpression expression, boolean equals, String qualifiedName1, String qualifiedName2) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (!(expression instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      if (equals) {
        if (!JavaTokenType.EQEQ.equals(binaryExpression.getOperationTokenType())) {
          return false;
        }
      }
      else {
        if (!JavaTokenType.NE.equals(binaryExpression.getOperationTokenType())) {
          return false;
        }
      }
      final PsiExpression leftOperand = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
      final PsiExpression rightOperand = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
      final String leftName = getQualifiedVariableName(leftOperand);
      final String rightName = getQualifiedVariableName(rightOperand);
      return leftName != null && rightName != null &&
             (leftName.equals(qualifiedName1) && rightName.equals(qualifiedName2) ||
              leftName.equals(qualifiedName2) && rightName.equals(qualifiedName1));
    }
  }

  private PsiExpression getArgumentExpression(PsiMethodCallExpression callExpression) {
    final PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
    return expressions.length == 1 ? ParenthesesUtils.stripParentheses(expressions[0]) : null;
  }

  /**
   * Check if the expression is a variable name chain like "a.b.c" ("this" and "super" are allowed), and convert it into a qualified name
   *
   * @return the text representation of the variable chain (with parenthesis stripped), or {@code null} if it's not a variable chain
   */
  @Nullable
  private static String getQualifiedVariableName(@Nullable PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final String referenceName = referenceExpression.getReferenceName();
      if (referenceName != null && referenceExpression.resolve() instanceof PsiVariable) {
        final PsiExpression qualifierExpression = ParenthesesUtils.stripParentheses(referenceExpression.getQualifierExpression());
        if (qualifierExpression == null) {
          return referenceName;
        }
        final String qualifierName = getQualifiedVariableName(qualifierExpression);
        return qualifierName != null ? qualifierName + "." + referenceName : null;
      }
    }
    else if (expression instanceof PsiQualifiedExpression) {
      final PsiJavaCodeReferenceElement qualifier = ((PsiQualifiedExpression)expression).getQualifier();
      final String name = expression instanceof PsiThisExpression ? "this" : "super";
      return qualifier != null ? qualifier.getQualifiedName() + "." + name : name;
    }
    return null;
  }
}
