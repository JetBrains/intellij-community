/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.codeInspection.utils.SideEffectChecker;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyConditionalCanBeConditionalCallInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return "Conditional expression can be conditional call";
  }

  @Override
  public String buildErrorString(Object... args) {
    return "Conditional expression can be call #loc";
  }

  @Override
  public GroovyFix buildFix(@NotNull PsiElement location) {
    return new CollapseConditionalFix();
  }

  private static class CollapseConditionalFix extends GroovyFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return "Replace with conditional call";
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
      final GrConditionalExpression expression = (GrConditionalExpression) descriptor.getPsiElement();
      final GrBinaryExpression binaryCondition = (GrBinaryExpression)PsiUtil.skipParentheses(expression.getCondition(), false);
      final GrMethodCallExpression call;
      if (GrInspectionUtil.isInequality(binaryCondition)) {
        call = (GrMethodCallExpression) expression.getThenBranch();
      } else {
        call = (GrMethodCallExpression) expression.getElseBranch();
      }
      final GrReferenceExpression methodExpression = (GrReferenceExpression) call.getInvokedExpression();
      final GrExpression qualifier = methodExpression.getQualifierExpression();
      final String methodName = methodExpression.getReferenceName();
      final GrArgumentList argumentList = call.getArgumentList();
      replaceExpression(expression, qualifier.getText() + "?." + methodName + argumentList.getText());
    }
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(@NotNull GrConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      GrExpression condition = expression.getCondition();
      final GrExpression thenBranch = expression.getThenBranch();
      final GrExpression elseBranch = expression.getElseBranch();
      if (thenBranch == null || elseBranch == null) {
        return;
      }
      if (SideEffectChecker.mayHaveSideEffects(condition)) {
        return;
      }
      condition = (GrExpression)PsiUtil.skipParentheses(condition, false);
      if (!(condition instanceof GrBinaryExpression)) {
        return;
      }
      final GrBinaryExpression binaryCondition = (GrBinaryExpression) condition;
      final GrExpression lhs = binaryCondition.getLeftOperand();
      final GrExpression rhs = binaryCondition.getRightOperand();
      if (GrInspectionUtil.isInequality(binaryCondition) && GrInspectionUtil.isNull(elseBranch)) {
        if (GrInspectionUtil.isNull(lhs) && isCallTargeting(thenBranch, rhs) ||
            GrInspectionUtil.isNull(rhs) && isCallTargeting(thenBranch, lhs)) {
          registerError(expression);
        }
      }

      if (GrInspectionUtil.isEquality(binaryCondition) && GrInspectionUtil.isNull(thenBranch)) {
        if (GrInspectionUtil.isNull(lhs) && isCallTargeting(elseBranch, rhs) ||
            GrInspectionUtil.isNull(rhs) && isCallTargeting(elseBranch, lhs)) {
          registerError(expression);
        }
      }
    }

    private static boolean isCallTargeting(GrExpression call, GrExpression expression) {
      if (!(call instanceof GrMethodCallExpression)) {
        return false;
      }
      final GrMethodCallExpression callExpression = (GrMethodCallExpression) call;
      final GrExpression methodExpression = callExpression.getInvokedExpression();
      if (!(methodExpression instanceof GrReferenceExpression)) {
        return false;
      }
      final GrReferenceExpression referenceExpression = (GrReferenceExpression) methodExpression;
      if (!GroovyTokenTypes.mDOT.equals(referenceExpression.getDotTokenType())) {
        return false;
      }
      return EquivalenceChecker.expressionsAreEquivalent(expression, referenceExpression.getQualifierExpression());
    }
  }
}