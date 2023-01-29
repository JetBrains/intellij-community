// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_FUNCTION_SUPPLIER;

public class ThrowableSupplierOnlyThrowExceptionInspection extends BaseInspection {
  private static final CallMatcher OPTIONAL_OR_ELSE_THROW = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "orElseThrow").parameterCount(1),
    CallMatcher.instanceCall("java.util.OptionalDouble", "orElseThrow").parameterCount(1),
    CallMatcher.instanceCall("java.util.OptionalInt", "orElseThrow").parameterCount(1),
    CallMatcher.instanceCall("java.util.OptionalLong", "orElseThrow").parameterCount(1)
  );

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[]{new ThrowToReturnQuickFix()};
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("throwable.supplier.only.throw.exception.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowableSupplierOnlyThrowExceptionVisitor();
  }

  private static class ThrowToReturnQuickFix extends InspectionGadgetsFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("throwable.supplier.only.throw.exception.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiLambdaExpression psiLambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      if (psiLambdaExpression == null) {
        return;
      }

      Collection<PsiThrowStatement> statements =
        PsiTreeUtil.collectElementsOfType(psiLambdaExpression.getBody(), PsiThrowStatement.class);

      List<PsiThrowStatement> throwStatements = ContainerUtil.findAll(statements, statement ->
        PsiTreeUtil.skipParentsOfType(statement, PsiCodeBlock.class, PsiStatement.class) == psiLambdaExpression);

      PsiElement returnStatement = null;
      for (PsiThrowStatement throwStatement : throwStatements) {
        CommentTracker tracker = new CommentTracker();
        StringBuilder builder = new StringBuilder();
        for (PsiElement child : throwStatement.getChildren()) {
          if (child instanceof PsiKeyword keyword && keyword.getTokenType() == JavaTokenType.THROW_KEYWORD) {
            builder.append("return");
          }
          else {
            builder.append(child.getText());
            tracker.grabComments(child);
          }
        }

        returnStatement = tracker.replaceAndRestoreComments(throwStatement, builder.toString());
      }
      if (returnStatement == null) {
        return;
      }
      PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(returnStatement, PsiLambdaExpression.class);
      if (lambdaExpression != null) {
        final PsiElement body = lambdaExpression.getBody();
        if (body != null) {
          PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
          if (expression != null) {
            body.replace(expression);
          }
        }
      }
    }
  }

  private static class ThrowableSupplierOnlyThrowExceptionVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!OPTIONAL_OR_ELSE_THROW.test(expression)) {
        return;
      }
      PsiExpression[] expressions = expression.getArgumentList().getExpressions();
      if (expressions.length != 1) {
        return;
      }
      PsiExpression supplier = expressions[0];
      if (!(supplier instanceof PsiLambdaExpression lambdaSupplier)) {
        return;
      }
      PsiType lambdaType = lambdaSupplier.getFunctionalInterfaceType();
      if (lambdaType == null ||
          !InheritanceUtil.isInheritor(lambdaType, JAVA_UTIL_FUNCTION_SUPPLIER) ||
          !(lambdaType instanceof PsiClassType classType && classType.getParameterCount() == 1) ||
          !InheritanceUtil.isInheritor(classType.getParameters()[0], CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }

      if (!ControlFlowUtils.lambdaExpressionAlwaysThrowsException(lambdaSupplier)) {
        return;
      }

      Collection<PsiThrowStatement> statements =
        PsiTreeUtil.collectElementsOfType(lambdaSupplier.getBody(), PsiThrowStatement.class);

      List<PsiThrowStatement> all = ContainerUtil.findAll(statements, statement ->
        PsiTreeUtil.skipParentsOfType(statement, PsiCodeBlock.class, PsiStatement.class) == lambdaSupplier);
      if (all.size() == 0) {
        return;
      }
      PsiThrowStatement lastThrowStatement = all.get(all.size() - 1);
      PsiElement highlightedElement = lastThrowStatement;
      for (PsiElement child : lastThrowStatement.getChildren()) {
        if (child instanceof PsiKeyword keyword && keyword.textMatches(PsiKeyword.THROW)) {
          highlightedElement = child;
          break;
        }
      }
      registerError(highlightedElement);
    }
  }
}
