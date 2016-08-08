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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PointlessBooleanExpressionInspection extends BaseInspection {

  private static final Set<IElementType> booleanTokens = new HashSet<>();
  static {
    booleanTokens.add(JavaTokenType.ANDAND);
    booleanTokens.add(JavaTokenType.AND);
    booleanTokens.add(JavaTokenType.OROR);
    booleanTokens.add(JavaTokenType.OR);
    booleanTokens.add(JavaTokenType.XOR);
    booleanTokens.add(JavaTokenType.EQEQ);
    booleanTokens.add(JavaTokenType.NE);
  }

  @SuppressWarnings("PublicField")
  public boolean m_ignoreExpressionsContainingConstants = true;

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message("pointless.boolean.expression.ignore.option"), this, "m_ignoreExpressionsContainingConstants"
    );
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("pointless.boolean.expression.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    return InspectionGadgetsBundle.message("boolean.expression.can.be.simplified.problem.descriptor",
                                           buildSimplifiedExpression(expression, new StringBuilder()).toString());
  }

  private StringBuilder buildSimplifiedExpression(@Nullable PsiExpression expression, StringBuilder out) {
    if (expression instanceof PsiPolyadicExpression) {
      buildSimplifiedPolyadicExpression((PsiPolyadicExpression)expression, out);
    }
    else if (expression instanceof PsiPrefixExpression) {
      buildSimplifiedPrefixExpression((PsiPrefixExpression)expression, out);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      final PsiExpression expression1 = parenthesizedExpression.getExpression();
      out.append('(');
      buildSimplifiedExpression(expression1, out);
      out.append(')');
    }
    else if (expression != null) {
      out.append(expression.getText());
    }
    return out;
  }

  private void buildSimplifiedPolyadicExpression(PsiPolyadicExpression expression, StringBuilder out) {
    final IElementType tokenType = expression.getOperationTokenType();
    final PsiExpression[] operands = expression.getOperands();
    final List<PsiExpression> expressions = new ArrayList<>();
    if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.AND)) {
      for (PsiExpression operand : operands) {
        if (evaluate(operand) == Boolean.TRUE) {
          continue;
        }
        if (evaluate(operand) == Boolean.FALSE) {
          out.append(PsiKeyword.FALSE);
          return;
        }
        expressions.add(operand);
      }
      if (expressions.isEmpty()) {
        out.append(PsiKeyword.TRUE);
        return;
      }
      buildSimplifiedExpression(expressions, tokenType.equals(JavaTokenType.ANDAND) ? "&&" : "&", false, out);
    } else if (tokenType.equals(JavaTokenType.OROR) || tokenType.equals(JavaTokenType.OR)) {
      for (PsiExpression operand : operands) {
        if (evaluate(operand) == Boolean.FALSE) {
          continue;
        }
        if (evaluate(operand) == Boolean.TRUE) {
          out.append(PsiKeyword.TRUE);
          return;
        }
        expressions.add(operand);
      }
      if (expressions.isEmpty()) {
        out.append(PsiKeyword.FALSE);
        return;
      }
      buildSimplifiedExpression(expressions, tokenType.equals(JavaTokenType.OROR) ? "||" : "|", false, out);
    }
    else if (tokenType.equals(JavaTokenType.XOR) || tokenType.equals(JavaTokenType.NE)) {
      boolean negate = false;
      for (PsiExpression operand : operands) {
        if (evaluate(operand) == Boolean.FALSE) {
          continue;
        }
        if (evaluate(operand) == Boolean.TRUE) {
          negate = !negate;
          continue;
        }
        expressions.add(operand);
      }
      if (expressions.isEmpty()) {
        if (negate) {
          out.append(PsiKeyword.TRUE);
        }
        else {
          out.append(PsiKeyword.FALSE);
        }
        return;
      }
      buildSimplifiedExpression(expressions, tokenType.equals(JavaTokenType.XOR) ? "^" : "!=", negate, out);
    }
    else if (tokenType.equals(JavaTokenType.EQEQ)) {
      boolean negate = false;
      for (PsiExpression operand : operands) {
        if (evaluate(operand) == Boolean.TRUE) {
          continue;
        }
        if (evaluate(operand) == Boolean.FALSE) {
          negate = !negate;
          continue;
        }
        expressions.add(operand);
      }
      if (expressions.isEmpty()) {
        if (negate) {
          out.append(PsiKeyword.FALSE);
        }
        else {
          out.append(PsiKeyword.TRUE);
        }
        return;
      }
      buildSimplifiedExpression(expressions, "==", negate, out);
    }
    else {
      out.append(expression.getText());
    }
  }

  private void buildSimplifiedExpression(List<PsiExpression> expressions, String token, boolean negate, StringBuilder out) {
    if (expressions.size() == 1) {
      final PsiExpression expression = expressions.get(0);
      if (!negate) {
        out.append(expression.getText());
        return;
      }
      if (ComparisonUtils.isComparison(expression)) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
        final String negatedComparison = ComparisonUtils.getNegatedComparison(binaryExpression.getOperationTokenType());
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        assert rhs != null;
        out.append(lhs.getText()).append(negatedComparison).append(rhs.getText());
      }
      else {
        if (ParenthesesUtils.getPrecedence(expression) > ParenthesesUtils.PREFIX_PRECEDENCE) {
          out.append("!(").append(expression.getText()).append(')');
        }
        else {
          out.append('!').append(expression.getText());
        }
      }
    }
    else {
      if (negate) {
        out.append("!(");
      }
      boolean useToken = false;
      for (PsiExpression expression : expressions) {
        if (useToken) {
          out.append(token);
          final PsiElement previousSibling = expression.getPrevSibling();
          if (previousSibling instanceof PsiWhiteSpace) {
            out.append(previousSibling.getText());
          }
        }
        else {
          useToken = true;
        }
        buildSimplifiedExpression(expression, out);
        final PsiElement nextSibling = expression.getNextSibling();
        if (nextSibling instanceof PsiWhiteSpace) {
          out.append(nextSibling.getText());
        }
      }
      if (negate) {
        out.append(')');
      }
    }
  }

  private void buildSimplifiedPrefixExpression(PsiPrefixExpression expression, StringBuilder out) {
    final PsiJavaToken sign = expression.getOperationSign();
    final IElementType tokenType = sign.getTokenType();
    final PsiExpression operand = expression.getOperand();
    if (JavaTokenType.EXCL.equals(tokenType)) {
      final Boolean value = evaluate(operand);
      if (value == Boolean.TRUE) {
        out.append(PsiKeyword.FALSE);
        return;
      }
      else if (value == Boolean.FALSE) {
        out.append(PsiKeyword.TRUE);
        return;
      }
    }
    buildSimplifiedExpression(operand, out.append(sign.getText()));
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new PointlessBooleanExpressionFix();
  }

  private class PointlessBooleanExpressionFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiExpression)) {
        return;
      }
      final PsiExpression expression = (PsiExpression)element;
      PsiReplacementUtil.replaceExpression(expression, buildSimplifiedExpression(expression, new StringBuilder()).toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessBooleanExpressionVisitor();
  }

  private class PointlessBooleanExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      if (!isPointlessBooleanExpression(expression)) {
        return;
      }
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (parent instanceof PsiExpression && isPointlessBooleanExpression((PsiExpression)parent)) {
        return;
      }
      registerError(expression, expression);
    }

    private boolean isPointlessBooleanExpression(PsiExpression expression) {
      if (expression instanceof PsiPrefixExpression) {
        return evaluate(expression) != null;
      }
      if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        final IElementType sign = polyadicExpression.getOperationTokenType();
        if (!booleanTokens.contains(sign)) {
          return false;
        }
        final PsiExpression[] operands = polyadicExpression.getOperands();
        boolean containsConstant = false;
        for (PsiExpression operand : operands) {
          if (operand == null) {
            return false;
          }
          final PsiType type = operand.getType();
          if (type == null || !type.equals(PsiType.BOOLEAN) && !type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
            return false;
          }
          containsConstant |= evaluate(operand) != null;
        }
        return containsConstant;
      }
      return false;
    }
  }

  @Nullable
  private Boolean evaluate(@Nullable PsiExpression expression) {
    if (expression == null || m_ignoreExpressionsContainingConstants && containsReference(expression)) {
      return null;
    }
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      return evaluate(parenthesizedExpression.getExpression());
    }
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.OROR)) {
        final PsiExpression[] operands = polyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (evaluate(operand) == Boolean.TRUE) {
            return Boolean.TRUE;
          }
        }
      }
      else if (tokenType.equals(JavaTokenType.ANDAND)) {
        final PsiExpression[] operands = polyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (evaluate(operand) == Boolean.FALSE) {
            return Boolean.FALSE;
          }
        }
      }
    }
    else if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (JavaTokenType.EXCL.equals(tokenType)) {
        final PsiExpression operand = prefixExpression.getOperand();
        final Boolean b = evaluate(operand);
        if (b == Boolean.FALSE) {
          return Boolean.TRUE;
        } else if (b == Boolean.TRUE) {
          return Boolean.FALSE;
        }
      }
    }
    final Boolean value = (Boolean)ConstantExpressionUtil.computeCastTo(expression, PsiType.BOOLEAN);
    return value != null ? value : null;
  }

  private static boolean containsReference(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final ReferenceVisitor visitor = new ReferenceVisitor();
    expression.accept(visitor);
    return visitor.containsReference();
  }

  private static class ReferenceVisitor extends JavaRecursiveElementWalkingVisitor {

    private boolean referenceFound;

    @Override
    public void visitElement(PsiElement element) {
      if (referenceFound) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      final PsiElement target = expression.resolve();
      if (target instanceof PsiField && ExpressionUtils.isConstant((PsiField)target)) {
        referenceFound = true;
      }
      else {
        super.visitReferenceExpression(expression);
      }
    }

    private boolean containsReference() {
      return referenceFound;
    }
  }
}