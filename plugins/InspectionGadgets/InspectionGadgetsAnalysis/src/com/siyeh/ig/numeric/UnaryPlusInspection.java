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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class UnaryPlusInspection extends BaseInspection {

  public boolean onlyReportInsideBinaryExpression = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unary.plus.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unary.plus.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Only report in confusing binary or unary expression context", this,
                                          "onlyReportInsideBinaryExpression");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!onlyReportInsideBinaryExpression) {
      node.addContent(new Element("option").setAttribute("name", "onlyReportInsideBinaryExpression").setAttribute("value", "false"));
    }
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnaryPlusFix();
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
  public BaseInspectionVisitor buildVisitor() {
    return new UnaryPlusVisitor();
  }

  private class UnaryPlusVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(PsiPrefixExpression prefixExpression) {
      super.visitPrefixExpression(prefixExpression);
      final PsiJavaToken token = prefixExpression.getOperationSign();
      final IElementType tokenType = token.getTokenType();
      if (!tokenType.equals(JavaTokenType.PLUS)) {
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
      if (onlyReportInsideBinaryExpression) {
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
      registerError(token, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
    }
  }
}