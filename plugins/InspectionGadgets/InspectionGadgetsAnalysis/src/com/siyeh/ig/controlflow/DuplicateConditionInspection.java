/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class DuplicateConditionInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignoreSideEffectConditions = true;

  // This is a dirty fix of 'squared' algorithm performance issue.
  private static final int LIMIT_DEPTH = 20;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("duplicate.condition.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("duplicate.condition.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("duplicate.condition.ignore.method.calls.option"),
                                          this, "ignoreSideEffectConditions");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DuplicateConditionVisitor();
  }

  private class DuplicateConditionVisitor extends BaseInspectionVisitor {
    private Set<PsiIfStatement> myAnalyzedStatements = new HashSet<>();

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      if (ControlFlowUtils.isElseIf(statement)) {
        return;
      }
      final Set<PsiExpression> conditions = new LinkedHashSet<>();
      collectConditionsForIfStatement(statement, conditions, 0);
      final int numConditions = conditions.size();
      if (numConditions < 2) {
        return;
      }
      final List<PsiExpression> conditionList = new ArrayList<>(conditions);
      if (ignoreSideEffectConditions) {
        conditionList.replaceAll(cond -> SideEffectChecker.mayHaveSideEffects(cond) ? null : cond);
        // Every condition having side-effect separates non-side-effect conditions into independent groups
        // like:
        // if(!readToken() || token == X || token == Y) ...
        // else if(!readToken() || token == X || token == Y) ...
        // here we analyze independently first ['token == X', 'token == Y'] and second ['token == X', 'token == Y']
        // thus no warning is issued. Such constructs often appear in parsers.
        StreamEx.of(conditionList).groupRuns((a, b) -> a != null && b != null)
          .filter(list -> list.size() >= 2).forEach(this::findDuplicates);
      } else {
        findDuplicates(conditionList);
      }
    }

    private void findDuplicates(List<PsiExpression> conditions) {
      final BitSet matched = new BitSet();

      for (int i = 0; i < conditions.size(); i++) {
        if (matched.get(i)) {
          continue;
        }
        final PsiExpression condition = conditions.get(i);
        for (int j = i + 1; j < conditions.size(); j++) {
          if (matched.get(j)) {
            continue;
          }
          final PsiExpression testCondition = conditions.get(j);
          final boolean areEquivalent = EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(condition, testCondition);
          if (areEquivalent) {
            registerError(testCondition);
            if (!matched.get(i)) {
              registerError(condition);
            }
            matched.set(i);
            matched.set(j);
          }
        }
      }
    }

    private void collectConditionsForIfStatement(PsiIfStatement statement, Set<PsiExpression> conditions, int depth) {
      if (depth > LIMIT_DEPTH || !myAnalyzedStatements.add(statement)) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      collectConditionsForExpression(condition, conditions);
      final PsiStatement branch = ControlFlowUtils.stripBraces(statement.getElseBranch());
      if (branch instanceof PsiIfStatement) {
        collectConditionsForIfStatement((PsiIfStatement)branch, conditions, depth + 1);
      }
      if (branch == null) {
        final PsiStatement thenBranch = statement.getThenBranch();
        if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
          return;
        }
        PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(statement);
        if(next instanceof PsiIfStatement) {
          collectConditionsForIfStatement((PsiIfStatement)next, conditions, depth + 1);
        }
      }
    }

    private void collectConditionsForExpression(PsiExpression condition, Set<PsiExpression> conditions) {
      if (condition == null) {
        return;
      }
      if (condition instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)condition;
        final PsiExpression contents = parenthesizedExpression.getExpression();
        collectConditionsForExpression(contents, conditions);
        return;
      }
      if (condition instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (JavaTokenType.OROR.equals(tokenType)) {
          final PsiExpression[] operands = polyadicExpression.getOperands();
          for (PsiExpression operand : operands) {
            collectConditionsForExpression(operand, conditions);
          }
          return;
        }
      }
      conditions.add(condition);
    }
  }
}
