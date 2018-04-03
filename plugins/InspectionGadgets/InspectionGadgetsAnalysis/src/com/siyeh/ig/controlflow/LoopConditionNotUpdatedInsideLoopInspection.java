/*
 * Copyright 2006-2014 Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class LoopConditionNotUpdatedInsideLoopInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignorePossibleNonLocalChanges = true;

  // Preserved for serialization compatibility
  @SuppressWarnings("unused")
  public boolean ignoreIterators = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "loop.condition.not.updated.inside.loop.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final boolean entireCondition = ((Boolean)infos[0]).booleanValue();
    if (entireCondition) {
      return InspectionGadgetsBundle.message("loop.condition.not.updated.inside.loop.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("loop.variable.not.updated.inside.loop.problem.descriptor");
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "ignorePossibleNonLocalChanges");
    writeBooleanOption(node, "ignorePossibleNonLocalChanges", true);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message("loop.variable.not.updated.inside.loop.option.nonlocal"),
      this, "ignorePossibleNonLocalChanges");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LoopConditionNotUpdatedInsideLoopVisitor();
  }

  private class LoopConditionNotUpdatedInsideLoopVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      final PsiExpression condition = statement.getCondition();
      check(condition, statement);
    }

    @Override
    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      final PsiExpression condition = statement.getCondition();
      check(condition, statement);
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      super.visitForStatement(statement);
      final PsiExpression condition = statement.getCondition();
      check(condition, statement);
    }

    private void check(@Nullable PsiExpression condition, @NotNull PsiLoopStatement statement) {
      final List<PsiExpression> notUpdated = new SmartList<>();
      PsiStatement body = statement.getBody();
      if (body == null || condition == null || SideEffectChecker.mayHaveSideEffects(condition)) return;
      if (ignorePossibleNonLocalChanges && !ExpressionUtils.isLocallyDefinedExpression(condition)) {
        if (SideEffectChecker.mayHaveNonLocalSideEffects(body)) return;
        if (statement instanceof PsiForStatement) {
          PsiStatement update = ((PsiForStatement)statement).getUpdate();
          if (update != null && SideEffectChecker.mayHaveNonLocalSideEffects(update)) return;
        }
      }
      if (isConditionNotUpdated(condition, statement, notUpdated)) {
        if (!ControlFlowUtils.statementMayCompleteNormally(body) && !ControlFlowUtils.statementIsContinueTarget(statement)) {
          // Such loop is reported by LoopStatementsThatDontLoopInspection, so no need to report
          // "Loop condition is not updated" if it's checked only once anyways.
          // Sometimes people write while(flag) {...; break;}
          // instead of if(flag) {...} just to be able to use break inside (though the 'if' could be labeled instead)
          return;
        }
        if (notUpdated.isEmpty()) {
          // condition involves only final variables and/or constants,
          // flag the whole condition
          if (!BoolUtils.isBooleanLiteral(condition)) {
            registerError(condition, Boolean.TRUE);
          }
        }
        else {
          for (PsiExpression expression : notUpdated) {
            registerError(expression, Boolean.FALSE);
          }
        }
      }
    }

    private boolean isConditionNotUpdated(@Nullable PsiExpression condition,
                                          @NotNull PsiStatement context,
                                          List<PsiExpression> notUpdated) {
      if (condition == null) {
        return false;
      }
      if (PsiUtil.isConstantExpression(condition) || ExpressionUtils.isNullLiteral(condition)) {
        return true;
      }
      if (condition instanceof PsiInstanceOfExpression) {
        final PsiInstanceOfExpression instanceOfExpression =
          (PsiInstanceOfExpression)condition;
        final PsiExpression operand = instanceOfExpression.getOperand();
        return isConditionNotUpdated(operand, context, notUpdated);
      }
      else if (condition instanceof PsiParenthesizedExpression) {
        // catch stuff like "while ((x)) { ... }"
        final PsiExpression expression =
          ((PsiParenthesizedExpression)condition).getExpression();
        return isConditionNotUpdated(expression, context, notUpdated);
      }
      else if (condition instanceof PsiPolyadicExpression) {
        // while (value != x) { ... }
        // while (value != (x + y)) { ... }
        // while (b1 && b2) { ... }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          if (!isConditionNotUpdated(operand, context, notUpdated)) {
            return false;
          }
        }
        return true;
      }
      else if (condition instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)condition;
        final PsiElement element = referenceExpression.resolve();
        if (element instanceof PsiField) {
          final PsiField field = (PsiField)element;
          final PsiType type = field.getType();
          if (field.hasModifierProperty(PsiModifier.FINAL) &&
              type.getArrayDimensions() == 0) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
              return true;
            }
            final PsiExpression qualifier =
              referenceExpression.getQualifierExpression();
            if (qualifier == null) {
              return true;
            }
            else if (isConditionNotUpdated(qualifier, context,
                                           notUpdated)) {
              return true;
            }
          }
        }
        else if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
          final PsiVariable variable = (PsiVariable)element;
          boolean isFinal = variable.hasModifierProperty(PsiModifier.FINAL);
          final PsiType type = variable.getType();
          boolean arrayUpdated = type instanceof PsiArrayType && VariableAccessUtils.arrayContentsAreAssigned(variable, context);
          if ((isFinal || !VariableAccessUtils.variableIsAssigned(variable, context)) && !arrayUpdated) {
            if (!isFinal) {
              notUpdated.add(referenceExpression);
            }
            return true;
          }
        }
      }
      else if (condition instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)condition;
        if (!PsiUtil.isIncrementDecrementOperation(prefixExpression)) {
          final PsiExpression operand = prefixExpression.getOperand();
          return isConditionNotUpdated(operand, context, notUpdated);
        }
      }
      else if (condition instanceof PsiArrayAccessExpression) {
        // Actually the contents of the array could change nevertheless
        // if it is accessed through a different reference like this:
        //   int[] local_ints = new int[]{1, 2};
        //   int[] other_ints = local_ints;
        //   while (local_ints[0] > 0) { other_ints[0]--; }
        //
        // Keep this check?
        final PsiArrayAccessExpression accessExpression = (PsiArrayAccessExpression)condition;
        final PsiExpression indexExpression = accessExpression.getIndexExpression();
        return isConditionNotUpdated(indexExpression, context, notUpdated)
               && isConditionNotUpdated(accessExpression.getArrayExpression(), context, notUpdated);
      }
      else if (condition instanceof PsiConditionalExpression) {
        final PsiConditionalExpression conditionalExpression =
          (PsiConditionalExpression)condition;
        final PsiExpression thenExpression =
          conditionalExpression.getThenExpression();
        final PsiExpression elseExpression =
          conditionalExpression.getElseExpression();
        if (thenExpression == null || elseExpression == null) {
          return false;
        }
        return isConditionNotUpdated(conditionalExpression.getCondition(), context, notUpdated)
               && isConditionNotUpdated(thenExpression, context, notUpdated)
               && isConditionNotUpdated(elseExpression, context, notUpdated);
      }
      else if (condition instanceof PsiMethodCallExpression) {
        PsiExpression qualifier = ((PsiMethodCallExpression)condition).getMethodExpression().getQualifierExpression();
        if (!isConditionNotUpdated(qualifier, context, notUpdated)) return false;
        for (PsiExpression arg : ((PsiMethodCallExpression)condition).getArgumentList().getExpressions()) {
          if (!isConditionNotUpdated(arg, context, notUpdated)) return false;
        }
        return true;
      }
      else if (condition instanceof PsiTypeCastExpression) {
        return isConditionNotUpdated(((PsiTypeCastExpression)condition).getOperand(), context, notUpdated);
      }
      else if (condition instanceof PsiThisExpression || condition instanceof PsiClassObjectAccessExpression) {
        return true;
      }
      return false;
    }
  }
}