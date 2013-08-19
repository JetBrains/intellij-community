/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DoubleCheckedLockingInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignoreOnVolatileVariables = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "double.checked.locking.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "double.checked.locking.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "double.checked.locking.ignore.on.volatiles.option"), this,
                                          "ignoreOnVolatileVariables"
    );
  }

  @Override
  protected InspectionGadgetsFix buildFix(final Object... infos) {
    final PsiField field = (PsiField)infos[0];
    if (field == null) {
      return null;
    }
    return new DoubleCheckedLockingFix(field);
  }

  private static class DoubleCheckedLockingFix extends InspectionGadgetsFix {

    private final PsiField field;

    private DoubleCheckedLockingFix(PsiField field) {
      this.field = field;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "double.checked.locking.quickfix", field.getName());
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Make field volatile";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList == null) {
        return;
      }
      modifierList.setModifierProperty(PsiModifier.VOLATILE, true);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DoubleCheckedLockingVisitor();
  }

  private class DoubleCheckedLockingVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(
      @NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiExpression outerCondition = statement.getCondition();
      if (outerCondition == null) {
        return;
      }
      if (SideEffectChecker.mayHaveSideEffects(outerCondition)) {
        return;
      }
      PsiStatement thenBranch = statement.getThenBranch();
      thenBranch = ControlFlowUtils.stripBraces(thenBranch);
      if (!(thenBranch instanceof PsiSynchronizedStatement)) {
        return;
      }
      final PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)thenBranch;
      final PsiCodeBlock body = synchronizedStatement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length != 1) {
        return;
      }
      final PsiStatement firstStatement = statements[0];
      if (!(firstStatement instanceof PsiIfStatement)) {
        return;
      }
      final PsiIfStatement innerIf = (PsiIfStatement)firstStatement;
      final PsiExpression innerCondition = innerIf.getCondition();
      if (!EquivalenceChecker.expressionsAreEquivalent(innerCondition,
                                                       outerCondition)) {
        return;
      }
      final PsiField field;
      if (ignoreOnVolatileVariables) {
        field = findCheckedField(innerCondition);
        if (field != null &&
            field.hasModifierProperty(PsiModifier.VOLATILE)) {
          return;
        }
      }
      else {
        field = null;
      }
      registerStatementError(statement, field);
    }

    @Nullable
    private PsiField findCheckedField(PsiExpression expression) {
      if (expression instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)expression;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiField)) {
          return null;
        }
        return (PsiField)target;
      }
      else if (expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        final IElementType tokenType =
          binaryExpression.getOperationTokenType();
        if (!JavaTokenType.EQEQ.equals(tokenType)
            && !JavaTokenType.NE.equals(tokenType)) {
          return null;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        final PsiField field = findCheckedField(lhs);
        if (field != null) {
          return field;
        }
        return findCheckedField(rhs);
      }
      else if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression =
          (PsiPrefixExpression)expression;
        final IElementType tokenType =
          prefixExpression.getOperationTokenType();
        if (!JavaTokenType.EXCL.equals(tokenType)) {
          return null;
        }
        return findCheckedField(prefixExpression.getOperand());
      }
      else {
        return null;
      }
    }
  }
}