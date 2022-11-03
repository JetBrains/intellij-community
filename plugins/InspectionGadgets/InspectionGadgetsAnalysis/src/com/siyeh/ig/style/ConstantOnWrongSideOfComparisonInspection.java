// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;

public class ConstantOnWrongSideOfComparisonInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean myConstantShouldGoLeft = true;
  public boolean myIgnoreNull = false;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return myConstantShouldGoLeft
           ? InspectionGadgetsBundle.message("constant.on.rhs.of.comparison.problem.descriptor")
           : InspectionGadgetsBundle.message("constant.on.lhs.of.comparison.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final String left = InspectionGadgetsBundle.message("constant.on.lhs.of.comparison.options.item.left");
    final String right = InspectionGadgetsBundle.message("constant.on.lhs.of.comparison.options.item.right");
    final ComboBox<String> comboBox = new ComboBox<>(new String[]{left, right});
    comboBox.setSelectedIndex(myConstantShouldGoLeft ? 0 : 1);
    comboBox.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        myConstantShouldGoLeft = e.getItem() == left;
      }
    });
    final InspectionOptionsPanel panel = new InspectionOptionsPanel(this);
    panel.addLabeledRow(JavaAnalysisBundle.message("inspection.constant.on.wrong.side.of.a.comparison.side.option"), comboBox);
    panel.addCheckbox(InspectionGadgetsBundle.message("checkbox.ignore.null.on.wrong.side"), "myIgnoreNull");
    return panel;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SwapComparisonFix();
  }

  private static class SwapComparisonFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("flip.comparison.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression expression = (PsiBinaryExpression)element;
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final String flippedComparison = ComparisonUtils.getFlippedComparison(expression.getOperationTokenType());
      if (flippedComparison == null) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final CommentTracker commentTracker = new CommentTracker();
      PsiReplacementUtil.replaceExpression(expression, commentTracker.text(rhs) + flippedComparison + commentTracker.text(lhs), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantOnSideOfComparisonVisitor();
  }

  private class ConstantOnSideOfComparisonVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      if (!isConstantExpression(myConstantShouldGoLeft ? lhs : rhs) &&
          isConstantExpression(myConstantShouldGoLeft ? rhs : lhs) &&
          !ErrorUtil.containsDeepError(expression)) {
        registerError(myConstantShouldGoLeft ? rhs : lhs);
      }
    }

    private boolean isConstantExpression(PsiExpression expression) {
      if (ExpressionUtils.isNullLiteral(expression)) return !myIgnoreNull;
      return PsiUtil.isConstantExpression(expression);
    }
  }
}