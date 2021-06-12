/*
 * Copyright 2006-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public final class UnaryPlusInspection extends LocalInspectionTool {
  public boolean onlyReportInsideBinaryExpression = true;

  @NotNull
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(JavaAnalysisBundle.message("inspection.unary.plus.unary.binary.option"), this,
                                          "onlyReportInsideBinaryExpression");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!onlyReportInsideBinaryExpression) {
      node.addContent(new Element("option").setAttribute("name", "onlyReportInsideBinaryExpression").setAttribute("value", "false"));
    }
  }

  private static class UnaryPlusFix extends InspectionGadgetsFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unary.plus.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiPrefixExpression)) {
        return;
      }
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
      final PsiExpression operand = prefixExpression.getOperand();
      if (operand == null) {
        return;
      }
      prefixExpression.replace(operand);
    }
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new UnaryPlusVisitor(holder, isOnTheFly, onlyReportInsideBinaryExpression);
  }

  private static class UnaryPlusVisitor extends BaseInspectionVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myOnTheFly;
    private final boolean myOnlyReportInsideBinaryExpression;

    private UnaryPlusVisitor(@NotNull ProblemsHolder holder, boolean onTheFly, boolean onlyReportInsideBinaryExpression) {
      myHolder = holder;
      myOnTheFly = onTheFly;
      myOnlyReportInsideBinaryExpression = onlyReportInsideBinaryExpression;
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression prefixExpression) {
      super.visitPrefixExpression(prefixExpression);
      if (!ConvertDoubleUnaryToPrefixOperationFix.isDesiredPrefixExpression(prefixExpression, true)) {
        return;
      }
      final PsiExpression operand = prefixExpression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType type = operand.getType();
      if (type == null) {
        return;
      }
      if (myOnlyReportInsideBinaryExpression) {
        final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(prefixExpression);
        if (!(operand instanceof PsiParenthesizedExpression ||
              operand instanceof PsiPrefixExpression ||
              parent instanceof PsiPolyadicExpression ||
              parent instanceof PsiPrefixExpression ||
              parent instanceof PsiAssignmentExpression ||
              parent instanceof PsiVariable)) {
          return;
        }
      }
      else if (TypeUtils.unaryNumericPromotion(type) != type &&
               MethodCallUtils.isNecessaryForSurroundingMethodCall(prefixExpression, operand)) {
        // unary plus might have been used as cast to int
        return;
      }
      List<LocalQuickFix> fixes = new SmartList<>(new UnaryPlusFix());
      if (myOnTheFly) {
        LocalQuickFix incrementFix = ConvertDoubleUnaryToPrefixOperationFix.createFix(prefixExpression);
        if (incrementFix != null) {
          fixes.add(incrementFix);
        }
      }
      myHolder.registerProblem(prefixExpression.getOperationSign(), InspectionGadgetsBundle.message("unary.plus.problem.descriptor"),
                               ProblemHighlightType.LIKE_UNUSED_SYMBOL, fixes.toArray(LocalQuickFix[]::new));
    }
  }

  static class ConvertDoubleUnaryToPrefixOperationFix extends InspectionGadgetsFix {
    private final String myRefName;
    private final boolean myIncrement;

    private ConvertDoubleUnaryToPrefixOperationFix(@NotNull String refName, boolean increment) {
      this.myRefName = refName;
      this.myIncrement = increment;
    }

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("convert.double.unary.quickfix", myIncrement ? "++" : "--", myRefName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("prefix.operation.quickfix.family.name");
    }

    @Nullable
    static LocalQuickFix createFix(@NotNull PsiPrefixExpression prefixExpr) {
      final PsiExpression operand = prefixExpr.getOperand();
      boolean increment;
      if (isDesiredPrefixExpression(prefixExpr, true)) {
        increment = true;
      }
      else if (isDesiredPrefixExpression(prefixExpr, false)) {
        increment = false;
      }
      else {
        return null;
      }
      if (operand instanceof PsiReferenceExpression) {
        final PsiPrefixExpression parentPrefixExpr = ObjectUtils.tryCast(prefixExpr.getParent(), PsiPrefixExpression.class);
        if (isDesiredPrefixExpression(parentPrefixExpr, increment) &&
            containsOnlyWhitespaceBetweenOperatorAndOperand(prefixExpr) &&
            containsOnlyWhitespaceBetweenOperatorAndOperand(parentPrefixExpr)) {
          return createFix((PsiReferenceExpression)operand, increment);
        }
      }
      else if (operand instanceof PsiPrefixExpression && isDesiredPrefixExpression((PsiPrefixExpression)operand, increment)) {
        final PsiExpression operandExpr = ((PsiPrefixExpression)operand).getOperand();
        final PsiReferenceExpression operandRefExpr = ObjectUtils.tryCast(operandExpr, PsiReferenceExpression.class);
        if (operandRefExpr != null &&
            containsOnlyWhitespaceBetweenOperatorAndOperand(prefixExpr) &&
            containsOnlyWhitespaceBetweenOperatorAndOperand((PsiPrefixExpression)operand)) {
          return createFix(operandRefExpr, increment);
        }
      }
      return null;
    }

    @Nullable
    private static LocalQuickFix createFix(@NotNull PsiReferenceExpression refExpr, boolean increment) {
      final String refName = refExpr.getReferenceName();
      if (refName == null) {
        return null;
      }
      final PsiPrefixExpression topPrefixExpr = ObjectUtils.tryCast(refExpr.getParent().getParent(), PsiPrefixExpression.class);
      if (topPrefixExpr == null) {
        return null;
      }
      final PsiType refExprType = refExpr.getType();
      if (TypeUtils.unaryNumericPromotion(refExprType) != refExprType &&
          MethodCallUtils.isNecessaryForSurroundingMethodCall(topPrefixExpr, refExpr)) {
        return null;
      }
      final PsiVariable resolved = ObjectUtils.tryCast(refExpr.resolve(), PsiVariable.class);
      if (resolved == null || resolved.hasModifierProperty(PsiModifier.FINAL)) {
        return null;
      }
      return new ConvertDoubleUnaryToPrefixOperationFix(refName, increment);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiPrefixExpression prefixExpr = ObjectUtils.tryCast(descriptor.getPsiElement().getParent(), PsiPrefixExpression.class);
      if (prefixExpr == null) {
        return;
      }
      final PsiExpression operand = prefixExpr.getOperand();
      final PsiExpression oldExpr;
      final PsiReferenceExpression refExpr;
      if (operand instanceof PsiReferenceExpression) {
        oldExpr = (PsiExpression)prefixExpr.getParent();
        refExpr = (PsiReferenceExpression)operand;
      }
      else if (operand instanceof PsiPrefixExpression) {
        oldExpr = prefixExpr;
        refExpr = (PsiReferenceExpression)((PsiPrefixExpression)operand).getOperand();
      }
      else {
        return;
      }
      if (refExpr == null || oldExpr == null) {
        return;
      }
      final String refName = refExpr.getReferenceName();
      if (refName == null) {
        return;
      }
      final String operatorText = myIncrement ? "++" : "--";
      PsiReplacementUtil.replaceExpression(oldExpr, operatorText + refName);
    }

    private static boolean containsOnlyWhitespaceBetweenOperatorAndOperand(@Nullable PsiPrefixExpression prefixExpression) {
      if (prefixExpression == null) return false;
      final PsiJavaToken operator = prefixExpression.getOperationSign();
      final PsiExpression operand = prefixExpression.getOperand();
      PsiElement nextSibling = operator.getNextSibling();
      while (nextSibling != operand) {
        if (!(nextSibling instanceof PsiWhiteSpace)) {
          return false;
        }
        nextSibling = nextSibling.getNextSibling();
      }
      return true;
    }

    static boolean isDesiredPrefixExpression(@Nullable PsiPrefixExpression prefixExpr, boolean increment) {
      return prefixExpr != null && (increment ? prefixExpr.getOperationTokenType().equals(JavaTokenType.PLUS) :
             prefixExpr.getOperationTokenType().equals(JavaTokenType.MINUS));
    }
  }
}