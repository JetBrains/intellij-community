// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
abstract class CastedLiteralMaybeJustLiteralInspection extends BaseInspection {

  @Override
  @NotNull
  protected final String buildErrorString(Object... infos) {
    final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)infos[0];
    final StringBuilder replacementText = buildReplacementText(typeCastExpression, new StringBuilder());
    return InspectionGadgetsBundle.message("int.literal.may.be.long.literal.problem.descriptor", replacementText);
  }

  @NotNull
  abstract String getSuffix();

  @NotNull
  abstract PsiType getLiteralBeforeType();

  @NotNull
  abstract PsiType getCastType();

  private StringBuilder buildReplacementText(PsiExpression expression, StringBuilder out) {
    if (expression instanceof PsiLiteralExpression) {
      out.append(expression.getText()).append(getSuffix());
    }
    else if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      out.append(prefixExpression.getOperationSign().getText());
      return buildReplacementText(prefixExpression.getOperand(), out);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      out.append('(');
      buildReplacementText(parenthesizedExpression.getExpression(), out);
      out.append(')');
    }
    else if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
      buildReplacementText(typeCastExpression.getOperand(), out);
    }
    else {
      assert false;
    }
    return out;
  }

  @Override
  protected final InspectionGadgetsFix buildFix(Object... infos) {
    final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)infos[0];
    final StringBuilder replacementText = buildReplacementText(typeCastExpression, new StringBuilder());
    return new ReplaceCastedLiteralWithJustLiteralFix(replacementText.toString());
  }

  private class ReplaceCastedLiteralWithJustLiteralFix extends InspectionGadgetsFix {

    private final String replacementString;

    public ReplaceCastedLiteralWithJustLiteralFix(String replacementString) {
      this.replacementString = replacementString;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("int.literal.may.be.long.literal.quickfix", replacementString);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with " + getCastType().getPresentableText() + " literal";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiTypeCastExpression)) {
        return;
      }
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)element;
      PsiReplacementUtil.replaceExpression(typeCastExpression, replacementString);
    }
  }

  @Override
  public final BaseInspectionVisitor buildVisitor() {
    return new CastedLiteralMayBeJustLiteralVisitor();
  }

  private class CastedLiteralMayBeJustLiteralVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!getLiteralBeforeType().equals(type)) {
        return;
      }
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiPrefixExpression || parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiTypeCastExpression)) {
        return;
      }
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)parent;
      final PsiType castType = typeCastExpression.getType();
      if (!getCastType().equals(castType)) {
        return;
      }
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(typeCastExpression, false);
      if (expectedType != null && !getCastType().equals(expectedType)) {
        // don't warn on red code.
        return;
      }
      registerError(typeCastExpression, typeCastExpression);
    }
  }
}
