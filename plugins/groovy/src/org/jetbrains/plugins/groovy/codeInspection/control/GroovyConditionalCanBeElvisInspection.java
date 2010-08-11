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
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.codeInspection.utils.SideEffectChecker;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyConditionalCanBeElvisInspection extends BaseInspection {

  @NotNull
  public String getDisplayName() {
    return "Conditional expression can be elvis";
  }

  @NotNull
  public String getGroupDisplayName() {
    return CONTROL_FLOW;
  }

  public String buildErrorString(Object... args) {
    return "Conditional expression can be elvis #loc";
  }

  public GroovyFix buildFix(PsiElement location) {
    return new CollapseConditionalFix();
  }

  private static class CollapseConditionalFix extends GroovyFix {

    @NotNull
    public String getName() {
      return "Replace with elvis";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final GrConditionalExpression expression = (GrConditionalExpression) descriptor.getPsiElement();
      final GrExpression thenBranch = expression.getThenBranch();
      final GrExpression elseBranch = expression.getElseBranch();
      final GrBinaryExpression binaryCondition = (GrBinaryExpression)PsiUtil.skipParentheses(expression.getCondition(), false);
      if (isInequality(binaryCondition)) {
        replaceExpression(expression, thenBranch.getText() + "?:" + elseBranch.getText());
      } else {
        replaceExpression(expression, elseBranch.getText() + "?:" + thenBranch.getText());
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitConditionalExpression(GrConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      GrExpression condition = expression.getCondition();
      final GrExpression thenBranch = expression.getThenBranch();
      final GrExpression elseBranch = expression.getElseBranch();
      if (condition == null || thenBranch == null || elseBranch == null) {
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
      if (isInequality(binaryCondition)) {
        final GrExpression lhs = binaryCondition.getLeftOperand();
        final GrExpression rhs = binaryCondition.getRightOperand();
        if (isNull(lhs) && EquivalenceChecker.expressionsAreEquivalent(rhs, thenBranch) ||
            isNull(rhs) && EquivalenceChecker.expressionsAreEquivalent(lhs, thenBranch)) {
          registerError(expression);
        }
      } else if (isEquality(binaryCondition)) {
        final GrExpression lhs = binaryCondition.getLeftOperand();
        final GrExpression rhs = binaryCondition.getRightOperand();
        if (isNull(lhs) && EquivalenceChecker.expressionsAreEquivalent(rhs, elseBranch) ||
            isNull(rhs) && EquivalenceChecker.expressionsAreEquivalent(lhs, elseBranch)) {
          registerError(expression);
        }
      }
    }
  }

  private static boolean isEquality(GrBinaryExpression binaryCondition) {
    final IElementType tokenType = binaryCondition.getOperationTokenType();
    return GroovyTokenTypes.mEQUAL == tokenType;
  }

  private static boolean isInequality(GrBinaryExpression binaryCondition) {
    final IElementType tokenType = binaryCondition.getOperationTokenType();
    return GroovyTokenTypes.mNOT_EQUAL == tokenType;
  }

  private static boolean isNull(GrExpression expression) {
    return expression != null && "null".equals(expression.getText());
  }
}