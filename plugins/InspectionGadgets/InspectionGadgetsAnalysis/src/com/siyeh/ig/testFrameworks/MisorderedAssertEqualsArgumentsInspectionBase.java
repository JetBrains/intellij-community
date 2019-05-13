/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.testFrameworks;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public abstract class MisorderedAssertEqualsArgumentsInspectionBase extends BaseInspection {

  @NonNls
  private static final Set<String> methodNames =
    ContainerUtil.newHashSet("assertEquals", "assertEqualsNoOrder", "assertNotEquals", "assertArrayEquals", "assertSame",
                             "assertNotSame", "failNotSame", "failNotEquals");

  public abstract boolean checkTestNG();

  @Override
  @NotNull
  public final String getDisplayName() {
    return InspectionGadgetsBundle.message("misordered.assert.equals.arguments.display.name");
  }

  @Override
  @NotNull
  protected final String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("misordered.assert.equals.arguments.problem.descriptor");
  }

  @Override
  public final InspectionGadgetsFix buildFix(Object... infos) {
    return new FlipArgumentsFix();
  }

  private class FlipArgumentsFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("misordered.assert.equals.arguments.flip.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = methodNameIdentifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)parent.getParent();
      if (callExpression == null) {
        return;
      }

      final ExpectedActual expectedActual = ExpectedActual.create(callExpression, checkTestNG());
      if (expectedActual == null) {
        return;
      }
      final PsiExpression expectedArgument = expectedActual.getExpected();
      final PsiExpression actualArgument = expectedActual.getActual();
      final PsiElement copy = expectedArgument.copy();
      expectedArgument.replace(actualArgument);
      actualArgument.replace(copy);
    }
  }

  @Override
  public final BaseInspectionVisitor buildVisitor() {
    return new MisorderedAssertEqualsParametersVisitor();
  }

  private static class ExpectedActual {
    private final PsiExpression myExpected;
    private final PsiExpression myActual;

    private ExpectedActual(PsiExpression expected, PsiExpression actual) {
      myExpected = expected;
      myActual = actual;
    }

    public PsiExpression getExpected() {
      return myExpected;
    }

    public PsiExpression getActual() {
      return myActual;
    }

    private static ExpectedActual create(PsiMethodCallExpression callExpression, boolean checkTestNG) {
      AssertHint hint = AssertHint.create(callExpression, methodName -> methodNames.contains(methodName) ? 2 : null, checkTestNG);
      if (hint == null) {
        return null;
      }

      PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();

      int index = hint.getArgIndex();
      final PsiExpression expectedArgument = arguments[checkTestNG ? index + 1 : index];
      final PsiExpression actualArgument = arguments[checkTestNG ? index : index + 1];

      return new ExpectedActual(expectedArgument, actualArgument);
    }

  }

  private class MisorderedAssertEqualsParametersVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final ExpectedActual expectedActual = ExpectedActual.create(expression, checkTestNG());
      if (expectedActual == null) {
        return;
      }

      if (looksLikeExpectedArgument(expectedActual.getExpected()) || !looksLikeExpectedArgument(expectedActual.getActual())) {
        return;
      }
      registerMethodCallError(expression);
    }

    private boolean looksLikeExpectedArgument(@Nullable PsiExpression expression) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      if (ExpressionUtils.computeConstantExpression(expression) != null || PsiType.NULL.equals(type)) {
        return true;
      }
      if (expression instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiEnumConstant) {
          return true;
        }
        else if ((target instanceof PsiField)) {
          final PsiField field = (PsiField)target;
          if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
            return true;
          }
        }
        else if (target instanceof PsiLocalVariable) {
          final PsiVariable variable = (PsiLocalVariable)target;
          final PsiExpression definition = DeclarationSearchUtils.findDefinition(referenceExpression, variable);
          if (LibraryUtil.isOnlyLibraryCodeUsed(definition)) {
            return true;
          }
        }
      }
      if (expression instanceof PsiCallExpression && type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass aClass = classType.resolve();
        if (aClass instanceof PsiCompiledElement) {
            return LibraryUtil.isOnlyLibraryCodeUsed(expression);
        }
      }
      return false;
    }
  }
}
