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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
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
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class StringEqualsEmptyStringInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.equals.empty.string.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final boolean useIsEmpty = ((Boolean)infos[0]).booleanValue();
    if (useIsEmpty) {
      return InspectionGadgetsBundle.message("string.equals.empty.string.is.empty.problem.descriptor");
    } else {
      return InspectionGadgetsBundle.message("string.equals.empty.string.problem.descriptor");
    }
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final boolean useIsEmpty = ((Boolean)infos[0]).booleanValue();
    return new StringEqualsEmptyStringFix(useIsEmpty);
  }

  private static class StringEqualsEmptyStringFix extends InspectionGadgetsFix {

    private final boolean useIsEmpty;

    public StringEqualsEmptyStringFix(boolean useIsEmpty) {
      this.useIsEmpty = useIsEmpty;
    }

    @Override
    @NotNull
    public String getName() {
      if (useIsEmpty) {
        return InspectionGadgetsBundle.message("string.equals.empty.string.isempty.quickfix");
      }
      else {
        return InspectionGadgetsBundle.message("string.equals.empty.string.quickfix");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify empty string check";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiIdentifier name = (PsiIdentifier)descriptor.getPsiElement();
      final PsiReferenceExpression expression = (PsiReferenceExpression)name.getParent();
      if (expression == null) {
        return;
      }
      final PsiMethodCallExpression call = (PsiMethodCallExpression)expression.getParent();
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression qualifier = expression.getQualifierExpression();
      final PsiExpression argument = arguments[0];
      final PsiExpression checkedExpression;
      final boolean addNullCheck;
      if (ExpressionUtils.isEmptyStringLiteral(argument)) {
        checkedExpression = getCheckedExpression(qualifier);
        addNullCheck = false;
      }
      else {
        checkedExpression = getCheckedExpression(argument);
        addNullCheck = !isCheckedForNull(checkedExpression);
      }
      final StringBuilder newExpression;
      if (addNullCheck) {
        newExpression = new StringBuilder(checkedExpression.getText());
        newExpression.append("!=null&&");
      }
      else {
        newExpression = new StringBuilder("");
      }
      final PsiElement parent = call.getParent();
      final PsiExpression expressionToReplace;
      if (parent instanceof PsiExpression) {
        final PsiExpression parentExpression = (PsiExpression)parent;
        if (BoolUtils.isNegation(parentExpression)) {
          expressionToReplace = parentExpression;
          if (useIsEmpty) {
            newExpression.append('!').append(checkedExpression.getText()).append(".isEmpty()");
          }
          else {
            newExpression.append(checkedExpression.getText()).append(".length()!=0");
          }
        }
        else {
          expressionToReplace = call;
          if (useIsEmpty) {
            newExpression.append(checkedExpression.getText()).append(".isEmpty()");
          }
          else {
            newExpression.append(checkedExpression.getText()).append(".length()==0");
          }
        }
      }
      else {
        expressionToReplace = call;
        if (useIsEmpty) {
          newExpression.append(checkedExpression.getText()).append(".isEmpty()");
        }
        else {
          newExpression.append(checkedExpression.getText()).append(".length()==0");
        }
      }

      CommentTracker commentTracker = new CommentTracker();
      commentTracker.markUnchanged(checkedExpression);
      PsiReplacementUtil.replaceExpression(expressionToReplace, newExpression.toString(), commentTracker);
    }

    private static boolean isCheckedForNull(PsiExpression expression) {
      final PsiPolyadicExpression polyadicExpression =
        PsiTreeUtil.getParentOfType(expression, PsiPolyadicExpression.class, true, PsiStatement.class, PsiVariable.class);
      if (polyadicExpression == null) {
        return false;
      }
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (PsiTreeUtil.isAncestor(operand, expression, true)) {
          return false;
        }
        if (!(operand instanceof PsiBinaryExpression)) {
          continue;
        }
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)operand;
        final IElementType operationTokenType = binaryExpression.getOperationTokenType();
        if (JavaTokenType.ANDAND.equals(tokenType)) {
          if (!JavaTokenType.NE.equals(operationTokenType)) {
            continue;
          }
        }
        else if (JavaTokenType.OROR.equals(tokenType)) {
          if (!JavaTokenType.EQEQ.equals(operationTokenType)) {
            continue;
          }
        }
        else {
          continue;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
          continue;
        }
        if (PsiType.NULL.equals(lhs.getType()) &&
            EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(expression, rhs)) {
          return true;
        }
        else if (PsiType.NULL.equals(rhs.getType()) && EquivalenceChecker.getCanonicalPsiEquivalence()
          .expressionsAreEquivalent(expression, lhs)) {
          return true;
        }
      }
      return false;
    }

    private PsiExpression getCheckedExpression(PsiExpression expression) {
      if (useIsEmpty || !(expression instanceof PsiMethodCallExpression)) {
        return expression;
      }
      // to replace stringBuffer.toString().equals("") with
      // stringBuffer.length() == 0
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return expression;
      }
      final PsiType type = qualifierExpression.getType();
      if (HardcodedMethodConstants.TO_STRING.equals(referenceName) && type != null && (type.equalsToText(
        CommonClassNames.JAVA_LANG_STRING_BUFFER) || type.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER))) {
        return qualifierExpression;
      }
      else {
        return expression;
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringEqualsEmptyStringVisitor();
  }

  private static class StringEqualsEmptyStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"equals".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiElement context = call.getParent();
      final boolean useIsEmpty = PsiUtil.isLanguageLevel6OrHigher(call);
      if (!useIsEmpty && context instanceof PsiExpressionStatement) {
        // cheesy, but necessary, because otherwise the quickfix will
        // produce uncompilable code (out of merely incorrect code).
        return;
      }

      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      final PsiExpression argument = arguments[0];
      if (ExpressionUtils.isEmptyStringLiteral(qualifier)) {
        final PsiType type = argument.getType();
        if (!TypeUtils.isJavaLangString(type)) {
          return;
        }
      }
      else if (ExpressionUtils.isEmptyStringLiteral(argument)) {
        if (qualifier == null) {
          return;
        }
        final PsiType type = qualifier.getType();
        if (!TypeUtils.isJavaLangString(type)) {
          return;
        }
      }
      else {
        return;
      }
      registerMethodCallError(call, Boolean.valueOf(useIsEmpty));
    }
  }
}