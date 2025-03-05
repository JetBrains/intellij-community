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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
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

public final class GroovyConditionalCanBeConditionalCallInspection extends BaseInspection {

  @Override
  public String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.conditional.expression.can.be.call");
  }

  @Override
  public LocalQuickFix buildFix(@NotNull PsiElement location) {
    return new CollapseConditionalFix();
  }

  private static class CollapseConditionalFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("intention.family.name.replace.with.conditional.call");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final GrConditionalExpression expression = (GrConditionalExpression) element;
      final GrBinaryExpression binaryCondition = (GrBinaryExpression)PsiUtil.skipParentheses(expression.getCondition(), false);
      if (binaryCondition == null) {
        return;
      }
      final GrExpression branch;
      if (GrInspectionUtil.isInequality(binaryCondition)) {
        branch = expression.getThenBranch();
      } else {
        branch = expression.getElseBranch();
      }
      if (!(branch instanceof GrMethodCallExpression call)) {
        return;
      }
      final GrReferenceExpression methodExpression = (GrReferenceExpression) call.getInvokedExpression();
      final GrExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final String methodName = methodExpression.getReferenceName();
      final GrArgumentList argumentList = call.getArgumentList();
      GrInspectionUtil.replaceExpression(expression, qualifier.getText() + "?." + methodName + argumentList.getText());
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
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
      if (!(condition instanceof GrBinaryExpression binaryCondition)) {
        return;
      }
      final GrExpression lhs = binaryCondition.getLeftOperand();
      final GrExpression rhs = binaryCondition.getRightOperand();
      if (rhs == null) {
        return;
      }
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
      if (!(call instanceof GrMethodCallExpression callExpression)) {
        return false;
      }
      final GrExpression methodExpression = callExpression.getInvokedExpression();
      if (!(methodExpression instanceof GrReferenceExpression referenceExpression)) {
        return false;
      }
      if (!GroovyTokenTypes.mDOT.equals(referenceExpression.getDotTokenType())) {
        return false;
      }
      return EquivalenceChecker.expressionsAreEquivalent(expression, referenceExpression.getQualifierExpression());
    }
  }
}