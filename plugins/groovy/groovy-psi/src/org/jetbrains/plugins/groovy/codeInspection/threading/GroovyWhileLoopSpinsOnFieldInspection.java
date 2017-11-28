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
package org.jetbrains.plugins.groovy.codeInspection.threading;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;

public class GroovyWhileLoopSpinsOnFieldInspection extends BaseInspection {

  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean ignoreNonEmtpyLoops = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return "While loop spins on field";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return "<code>#ref</code> loop spins on field #loc";
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Only warn if loop is empty",
        this, "ignoreNonEmtpyLoops");
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WhileLoopSpinsOnFieldVisitor();
  }

  private class WhileLoopSpinsOnFieldVisitor
      extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@NotNull GrWhileStatement statement) {
      super.visitWhileStatement(statement);
      final GrStatement body = statement.getBody();
      if (ignoreNonEmtpyLoops && !statementIsEmpty(body)) {
        return;
      }
      final GrCondition condition = statement.getCondition();
      if (!(condition instanceof GrExpression)) {
        return;
      }
      if (!isSimpleFieldComparison((GrExpression) condition)) {
        return;
      }
      registerStatementError(statement);
    }

    private boolean isSimpleFieldComparison(GrExpression condition) {
      condition = (GrExpression)PsiUtil.skipParentheses(condition, false);
      if (condition == null) {
        return false;
      }
      if (isSimpleFieldAccess(condition)) {
        return true;
      }

      if (condition instanceof GrUnaryExpression && ((GrUnaryExpression)condition).isPostfix()) {
        final GrUnaryExpression postfixExpression = (GrUnaryExpression) condition;
        final GrExpression operand =
            postfixExpression.getOperand();
        return isSimpleFieldComparison(operand);
      }
      if (condition instanceof GrUnaryExpression) {
        final GrUnaryExpression unaryExpression =
            (GrUnaryExpression) condition;
        final GrExpression operand =
            unaryExpression.getOperand();
        return isSimpleFieldComparison(operand);
      }
      if (condition instanceof GrBinaryExpression) {
        final GrBinaryExpression binaryExpression =
            (GrBinaryExpression) condition;
        final GrExpression lOperand = binaryExpression.getLeftOperand();
        final GrExpression rOperand = binaryExpression.getRightOperand();
        if (isLiteral(rOperand)) {
          return isSimpleFieldComparison(lOperand);
        } else if (isLiteral(lOperand)) {
          return isSimpleFieldComparison(rOperand);
        } else {
          return false;
        }
      }
      return false;
    }

    private boolean isLiteral(GrExpression expression) {
      expression = (GrExpression)PsiUtil.skipParentheses(expression, false);
      if (expression == null) {
        return false;
      }
      return expression instanceof PsiLiteralExpression;
    }

    private boolean isSimpleFieldAccess(GrExpression expression) {
      expression = (GrExpression)PsiUtil.skipParentheses(expression, false);
      if (expression == null) {
        return false;
      }
      if (!(expression instanceof GrReferenceExpression)) {
        return false;
      }
      final GrReferenceExpression reference =
          (GrReferenceExpression) expression;
      final GrExpression qualifierExpression =
          reference.getQualifierExpression();
      if (qualifierExpression != null) {
        return false;
      }
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiField)) {
        return false;
      }
      final PsiField field = (PsiField) referent;
      return !field.hasModifierProperty(PsiModifier.VOLATILE);
    }

    private boolean statementIsEmpty(GrStatement statement) {
      if (statement == null) {
        return false;
      }
      if (statement instanceof GrBlockStatement) {
        final GrBlockStatement blockStatement =
            (GrBlockStatement) statement;
        final GrOpenBlock codeBlock = blockStatement.getBlock();
        final GrStatement[] codeBlockStatements = codeBlock.getStatements();
        for (GrStatement codeBlockStatement : codeBlockStatements) {
          if (!statementIsEmpty(codeBlockStatement)) {
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }
}
