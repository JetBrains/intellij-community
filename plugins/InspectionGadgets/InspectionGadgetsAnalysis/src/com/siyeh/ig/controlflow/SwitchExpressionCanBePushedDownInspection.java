// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SwitchExpressionCanBePushedDownInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.SWITCH_EXPRESSION.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
        processBlock(expression);
      }

      @Override
      public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
        processBlock(statement);
      }

      private void processBlock(PsiSwitchBlock block) {
        List<PsiExpression> branches = extractBranches(block);
        if (branches == null) return;
        PsiExpression[] diffs = findDiffs(block, branches);
        if (diffs.length != 0) {
          holder.registerProblem(block.getFirstChild(),
                                 InspectionGadgetsBundle.message("inspection.common.subexpression.in.switch.display.name"),
                                 new PushDownSwitchExpressionFix());
        }
      }
    };
  }

  private static @NotNull PsiExpression @NotNull [] findDiffs(@NotNull PsiSwitchBlock block, @NotNull List<PsiExpression> branches) {
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    PsiExpression[] diffs = new PsiExpression[branches.size()];
    for (int i = 1; i < branches.size(); i++) {
      EquivalenceChecker.Match match = equivalence.expressionsMatch(branches.get(0), branches.get(i));
      if (match.isExactMismatch()) return PsiExpression.EMPTY_ARRAY;
      if (match.isExactMatch()) {
        diffs[i] = diffs[0];
        continue;
      }
      if (!(match.getLeftDiff() instanceof PsiExpression first) ||
          !(match.getRightDiff() instanceof PsiExpression cur) ||
          diffs[0] != null && diffs[0] != first) {
        return PsiExpression.EMPTY_ARRAY;
      }
      if (PsiTypes.voidType().equals(first.getType()) || PsiTypes.voidType().equals(cur.getType())) return PsiExpression.EMPTY_ARRAY;
      if (diffs[0] == null) {
        for (int j = 0; j < i; j++) {
          if (diffs[j] == null) diffs[j] = first;
        }
      }
      diffs[i] = cur;
    }
    if (ArrayUtil.contains(null, diffs)) return PsiExpression.EMPTY_ARRAY;
    if (block instanceof PsiSwitchStatement statement && isNonExhaustiveSwitchStatement(statement)) {
      return PsiExpression.EMPTY_ARRAY;
    }
    return diffs;
  }

  private static boolean isNonExhaustiveSwitchStatement(PsiSwitchStatement statement) {
    PsiExpression selector = statement.getExpression();
    if (selector == null) return false;
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(selector.getType());
    if (selectorClass == null || !selectorClass.isEnum()) {
      return SwitchUtils.findDefaultElement(statement) == null;
    }
    Set<PsiEnumConstant> missingConstants = StreamEx.of(selectorClass.getFields()).select(PsiEnumConstant.class).toMutableSet();
    for (PsiSwitchLabelStatementBase child : PsiTreeUtil.getChildrenOfTypeAsList(statement.getBody(), PsiSwitchLabelStatementBase.class)) {
      if (SwitchUtils.findDefaultElement(child) != null) return false;
      for (PsiEnumConstant constant : SwitchUtils.findEnumConstants(child)) {
        missingConstants.remove(constant);
        if (missingConstants.isEmpty()) return false;
      }
    }
    // Don't need to care about pattern switches (e.g., over sealed types), as non-exhaustive pattern switch statements are non-compilable
    return true;
  }

  @Nullable
  private static List<PsiExpression> extractBranches(PsiSwitchBlock block) {
    PsiCodeBlock body = block.getBody();
    if (body == null) return null;
    PsiStatement[] statements = body.getStatements();
    if (statements.length < 2) return null;
    List<PsiExpression> result = new ArrayList<>();
    for (PsiStatement statement : statements) {
      if (!(statement instanceof PsiSwitchLabeledRuleStatement rule)) return null;
      PsiStatement ruleBody = rule.getBody();
      if (ruleBody instanceof PsiThrowStatement) continue;
      if (ruleBody instanceof PsiBlockStatement blockStatement) {
        if (!(block instanceof PsiSwitchExpression)) return null;
        if (ControlFlowUtils.codeBlockMayCompleteNormally(blockStatement.getCodeBlock())) return null;
        Collection<PsiYieldStatement> yields = PsiTreeUtil.findChildrenOfType(blockStatement, PsiYieldStatement.class);
        List<PsiYieldStatement> myYields = ContainerUtil.filter(yields, st -> st.findEnclosingExpression() == block);
        for (PsiYieldStatement yield : myYields) {
          PsiExpression expression = yield.getExpression();
          if (expression == null) return null;
          result.add(expression);
        }
        continue;
      }
      if (ruleBody instanceof PsiExpressionStatement expr) {
        result.add(expr.getExpression());
        continue;
      }
      return null;
    }
    if (result.size() < 2) return null;
    return result;
  }

  private static class PushDownSwitchExpressionFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.common.subexpression.in.switch.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiSwitchBlock block = PsiTreeUtil.getParentOfType(element, PsiSwitchBlock.class);
      if (block == null) return;
      List<PsiExpression> branches = extractBranches(block);
      if (branches == null) return;
      PsiExpression[] diffs = findDiffs(block, branches);
      if (diffs.length == 0) return;

      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiSwitchExpression newSwitch =
        (PsiSwitchExpression)factory.createExpressionFromText("switch(x) {}", block);
      PsiExpression selector = block.getExpression();
      PsiExpression newSelector = Objects.requireNonNull(newSwitch.getExpression());
      if (selector == null) {
        newSelector.delete();
      } else {
        newSelector.replace(selector);
      }
      Objects.requireNonNull(newSwitch.getBody()).replace(Objects.requireNonNull(block.getBody()));
      List<PsiExpression> newBranches = Objects.requireNonNull(extractBranches(newSwitch));
      for (int i = 0; i < newBranches.size(); i++) {
        newBranches.get(i).replace(diffs[i]);
      }
      diffs[0].replace(newSwitch);
      PsiExpression expression = branches.get(0);
      if (block instanceof PsiSwitchExpression) {
        block.replace(expression);
      } else {
        PsiExpressionStatement newStatement = (PsiExpressionStatement)factory.createStatementFromText("x;", block);
        newStatement.getExpression().replace(expression);
        block.replace(newStatement);
      }
    }
  }
}
