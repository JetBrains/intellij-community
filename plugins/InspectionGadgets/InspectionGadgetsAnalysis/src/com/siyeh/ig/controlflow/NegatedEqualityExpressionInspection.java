/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class NegatedEqualityExpressionInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("negated.equality.expression.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("negated.equality.expression.problem.descriptor", infos[0]);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new NegatedEqualityExpressionFix();
  }

  private static class NegatedEqualityExpressionFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("negated.equality.expression.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiPrefixExpression)) {
        return;
      }
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
      if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
        return;
      }
      final PsiExpression operand = ParenthesesUtils.stripParentheses(prefixExpression.getOperand());
      if (!(operand instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)operand;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      StringBuilder text = new StringBuilder(binaryExpression.getLOperand().getText());
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        text.append("!=");
      }
      else if (JavaTokenType.NE.equals(tokenType)) {
        text.append("==");
      }
      else {
        return;
      }
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs != null) {
        text.append(rhs.getText());
      }
      PsiReplacementUtil.replaceExpression(prefixExpression, text.toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NegatedEqualsVisitor();
  }

  private static class NegatedEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (!JavaTokenType.EXCL.equals(expression.getOperationTokenType())) {
        return;
      }
      final PsiExpression operand = ParenthesesUtils.stripParentheses(expression.getOperand());
      if (!(operand instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)operand;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        registerError(expression.getOperationSign(), "==");
      }
      else if (JavaTokenType.NE.equals(tokenType)) {
        registerError(expression.getOperationSign(), "!=");
      }
    }
  }
}
