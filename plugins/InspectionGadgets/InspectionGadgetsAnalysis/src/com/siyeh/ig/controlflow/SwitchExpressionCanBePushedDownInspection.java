// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
      if (PsiType.VOID.equals(first.getType()) || PsiType.VOID.equals(cur.getType())) return PsiExpression.EMPTY_ARRAY;
      if (diffs[0] == null) {
        for (int j = 0; j < i; j++) {
          if (diffs[j] == null) diffs[j] = first;
        }
      }
      diffs[i] = cur;
    }
    if (ArrayUtil.contains(null, diffs)) return PsiExpression.EMPTY_ARRAY;
    if (block instanceof PsiSwitchStatement && SwitchBlockHighlightingModel.createAddDefaultFixIfNecessary(block) != null) {
      return PsiExpression.EMPTY_ARRAY;
    }
    return diffs;
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
      if (!(ruleBody instanceof PsiExpressionStatement expr)) return null;
      result.add(expr.getExpression());
    }
    if (result.size() < 2) return null;
    return result;
  }

  private static class PushDownSwitchExpressionFix implements LocalQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.common.subexpression.in.switch.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiSwitchBlock block = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiSwitchBlock.class);
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
