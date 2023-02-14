// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.fixes.IgnoreClassFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public class SizeReplaceableByIsEmptyInspection extends BaseInspection {
  @SuppressWarnings({"PublicField"})
  public boolean ignoreNegations = false;
  @SuppressWarnings("PublicField")
  public OrderedSet<String> ignoredTypes = new OrderedSet<>();

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("expression.can.be.replaced.problem.descriptor", infos[0]);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("ignoredTypes", InspectionGadgetsBundle.message("options.label.ignored.classes"),
                 new JavaClassValidator().withTitle(InspectionGadgetsBundle.message("choose.class.type.to.ignore"))),
      checkbox("ignoreNegations", InspectionGadgetsBundle.message("size.replaceable.by.isempty.negation.ignore.option"))
    );
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> result = new SmartList<>();
    final PsiExpression expression = (PsiExpression)infos[1];
    final String methodName = (String)infos[2];
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
    if (aClass != null) {
      final String name = aClass.getQualifiedName();
      if (name != null) {
        result.add(new IgnoreClassFix(name, ignoredTypes,
                                      InspectionGadgetsBundle.message("size.replaceable.by.isempty.fix.ignore.calls", methodName, name)));
      }
    }
    result.add(new SizeReplaceableByIsEmptyFix());
    return result.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  protected static class SizeReplaceableByIsEmptyFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "isEmpty()");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)descriptor.getPsiElement();
      PsiExpression operand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
      if (!(operand instanceof PsiMethodCallExpression)) {
        operand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
      }
      if (!(operand instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      @NonNls String newExpression = commentTracker.text(qualifierExpression);
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!JavaTokenType.EQEQ.equals(tokenType)) {
        newExpression = '!' + newExpression;
      }
      newExpression += ".isEmpty()";

      PsiReplacementUtil.replaceExpression(binaryExpression, newExpression, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SizeReplaceableByIsEmptyVisitor();
  }

  private class SizeReplaceableByIsEmptyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isComparison(expression)) {
        return;
      }
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
      final boolean flipped;
      if (lhs instanceof PsiMethodCallExpression) {
        flipped = false;
      }
      else if (rhs instanceof PsiMethodCallExpression) {
        flipped = true;
      }
      else {
        return;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)(flipped ? rhs : lhs);
      @NonNls String isEmptyCall = null;
      final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.SIZE.equals(referenceName) && !HardcodedMethodConstants.LENGTH.equals(referenceName)) {
        return;
      }
      if (!callExpression.getArgumentList().isEmpty()) {
        return;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(qualifierExpression.getType());
      if (aClass == null || PsiTreeUtil.isAncestor(aClass, callExpression, true)) {
        return;
      }
      for (String ignoredType : ignoredTypes) {
        if (InheritanceUtil.isInheritor(aClass, ignoredType)) {
          return;
        }
      }
      for (PsiMethod method : aClass.findMethodsByName("isEmpty", true)) {
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.isEmpty()) {
          isEmptyCall = qualifierExpression.getText() + ".isEmpty()";
          break;
        }
      }
      if (isEmptyCall == null) {
        return;
      }
      final Object object = ExpressionUtils.computeConstantExpression(flipped ? lhs : rhs);
      if (!(object instanceof Integer) || ((Integer)object).intValue() != 0) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        registerError(expression, isEmptyCall, qualifierExpression, referenceName);
      }
      if (ignoreNegations) {
        return;
      }
      if (JavaTokenType.NE.equals(tokenType)) {
        registerError(expression, '!' + isEmptyCall, qualifierExpression, referenceName);
      }
      else if (flipped) {
        if (JavaTokenType.LT.equals(tokenType)) {
          registerError(expression, '!' + isEmptyCall, qualifierExpression, referenceName);
        }
      }
      else if (JavaTokenType.GT.equals(tokenType)) {
        registerError(expression, '!' + isEmptyCall, qualifierExpression, referenceName);
      }
    }
  }
}