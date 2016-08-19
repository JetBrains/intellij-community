/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.equality;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class EqualityOperatorComparesObjectsInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("equality.operator.compares.objects.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equality.operator.compares.objects.descriptor", infos);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectEqualityVisitor();
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[]{new EqualsFix(infos), new SafeEqualsFix(infos)};
  }

  private static void doFixImpl(@NotNull PsiElement element) {
    final PsiBinaryExpression exp = (PsiBinaryExpression)element;
    final PsiExpression lhs = exp.getLOperand();
    final PsiExpression rhs = exp.getROperand();
    if (rhs == null) {
      return;
    }
    final PsiExpression strippedLhs = ParenthesesUtils.stripParentheses(lhs);
    if (strippedLhs == null) {
      return;
    }
    final PsiExpression strippedRhs = ParenthesesUtils.stripParentheses(rhs);
    if (strippedRhs == null) {
      return;
    }
    final String lhText = strippedLhs.getText();
    final String rhText = strippedRhs.getText();

    final String prefix = exp.getOperationTokenType().equals(JavaTokenType.EQEQ) ? "" : "!";
    @NonNls final String expString;
    if (ParenthesesUtils.getPrecedence(strippedLhs) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
      expString = prefix + '(' + lhText + ").equals(" + rhText + ')';
    }
    else {
      expString = prefix + lhText + ".equals(" + rhText + ')';
    }
    PsiReplacementUtil.replaceExpression(exp, expString);
  }

  private static void doSafeFixImpl(PsiElement element) {
    final PsiBinaryExpression exp = (PsiBinaryExpression)element;
    final PsiExpression lhs = exp.getLOperand();
    final PsiExpression rhs = exp.getROperand();
    if (rhs == null) {
      return;
    }
    final PsiExpression strippedLhs =
      ParenthesesUtils.stripParentheses(lhs);
    if (strippedLhs == null) {
      return;
    }
    final PsiExpression strippedRhs =
      ParenthesesUtils.stripParentheses(rhs);
    if (strippedRhs == null) {
      return;
    }
    final String lhsText = strippedLhs.getText();
    final String rhsText = strippedRhs.getText();
    final PsiJavaToken operationSign = exp.getOperationSign();
    final IElementType tokenType = operationSign.getTokenType();
    final String signText = operationSign.getText();
    @NonNls final StringBuilder newExpression = new StringBuilder();
    if (PsiUtil.isLanguageLevel7OrHigher(element) && ClassUtils.findClass("java.util.Objects", element) != null) {
      if (tokenType.equals(JavaTokenType.NE)) {
        newExpression.append('!');
      }
      newExpression.append("java.util.Objects.equals(").append(lhsText).append(',').append(rhsText).append(')');
    }
    else {
      newExpression.append(lhsText).append("==null?").append(rhsText).append(signText).append(" null:");
      if (tokenType.equals(JavaTokenType.NE)) {
        newExpression.append('!');
      }
      if (ParenthesesUtils.getPrecedence(strippedLhs) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
        newExpression.append('(').append(lhsText).append(')');
      }
      else {
        newExpression.append(lhsText);
      }
      newExpression.append(".equals(").append(rhsText).append(')');
    }
    PsiReplacementUtil.replaceExpressionAndShorten(exp, newExpression.toString());
  }

  private static class SafeEqualsFix extends InspectionGadgetsFix {
    private final Object[] myInfos;

    public SafeEqualsFix(Object... infos) {
      myInfos = infos;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("equality.operator.compares.objects.safe.quickfix", myInfos);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("equality.operator.compares.objects.safe.family.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      doSafeFixImpl(descriptor.getPsiElement());
    }
  }

  private static class EqualsFix extends InspectionGadgetsFix {
    private final Object[] myInfos;

    public EqualsFix(Object... infos) {
      myInfos = infos;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("equality.operator.compares.objects.quickfix", myInfos);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("equality.operator.compares.objects.family.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      doFixImpl(descriptor.getPsiElement());
    }
  }

  private static class ObjectEqualityVisitor extends BaseInspectionVisitor {
    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.NE) &&
          !tokenType.equals(JavaTokenType.EQEQ)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiType lhsType = lhs.getType();
      if (lhsType == null || lhsType instanceof PsiPrimitiveType || TypeConversionUtil.isEnumType(lhsType)) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiType rhsType = rhs.getType();
      if (rhsType == null || rhsType instanceof PsiPrimitiveType || TypeConversionUtil.isEnumType(rhsType)) {
        return;
      }
      final String operationText = expression.getOperationSign().getText();
      final String prefix = tokenType.equals(JavaTokenType.NE) ? "!" : "";
      registerError(expression, operationText, prefix);
    }
  }
}