// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.testFrameworks;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class MisorderedAssertEqualsArgumentsInspection extends BaseInspection {

  @NonNls
  private static final Set<String> methodNames =
    ContainerUtil.newHashSet("assertEquals", "assertEqualsNoOrder", "assertNotEquals", "assertArrayEquals", "assertSame",
                             "assertNotSame", "failNotSame", "failNotEquals");

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
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = methodNameIdentifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)parent.getParent();
      if (callExpression == null) {
        return;
      }

      final AssertHint hint = createAssertHint(callExpression);
      if (hint == null) {
        return;
      }
      final PsiExpression expectedArgument = hint.getExpected();
      final PsiExpression actualArgument = hint.getActual();
      final PsiElement copy = expectedArgument.copy();
      expectedArgument.replace(actualArgument);
      actualArgument.replace(copy);
    }
  }

  AssertHint createAssertHint(@NotNull PsiMethodCallExpression expression) {
    return AssertHint.create(expression, methodName -> methodNames.contains(methodName) ? 2 : null);
  }

  static boolean looksLikeExpectedArgument(PsiExpression expression) {
    if (expression == null) {
      return false;
    }

    final Ref<Boolean> expectedArgument = Ref.create(Boolean.TRUE);
    final List<PsiExpression> expressions = new SmartList<>();
    expressions.add(expression);
    while (!expressions.isEmpty()) {
      expressions.remove(expressions.size() - 1).accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
          if (!expectedArgument.get().booleanValue()) {
            return;
          }
          super.visitReferenceExpression(referenceExpression);
          final PsiElement target = referenceExpression.resolve();
          if (target instanceof PsiEnumConstant || target instanceof PsiClass) {
            return;
          }
          else if (target instanceof PsiField) {
            final PsiField field = (PsiField)target;
            if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
              return;
            }
          }
          else if (target instanceof PsiParameter) {
            final PsiParameter parameter = (PsiParameter)target;
            if ("expected".equals(parameter.getName())) {
              return;
            }
            expectedArgument.set(Boolean.FALSE);
            return;
          }
          else if (target instanceof PsiLocalVariable) {
            final PsiVariable variable = (PsiLocalVariable)target;
            final PsiCodeBlock block = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (block == null) {
              return; // broken code
            }
            final PsiExpression definition = DeclarationSearchUtils.findDefinition(referenceExpression, variable);
            if (definition == null) {
              expectedArgument.set(Boolean.FALSE);
              return;
            }
            if (PsiUtil.isConstantExpression(definition) || PsiType.NULL.equals(definition.getType())) {
              return;
            }
            final PsiElement[] refs = DefUseUtil.getRefs(block, variable, definition);
            final int offset = referenceExpression.getTextOffset();
            for (PsiElement ref : refs) {
              if (ref.getTextOffset() < offset) {
                expectedArgument.set(Boolean.FALSE);
                return;
              }
            }
            expressions.add(definition);
          }
          if (!(target instanceof PsiCompiledElement)) {
            expectedArgument.set(Boolean.FALSE);
          }
        }
      });
    }
    return expectedArgument.get().booleanValue();
  }

  @Override
  public final BaseInspectionVisitor buildVisitor() {
    return new MisorderedAssertEqualsParametersVisitor();
  }

  private class MisorderedAssertEqualsParametersVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final AssertHint hint = createAssertHint(expression);
      if (hint == null) {
        return;
      }
      if (looksLikeExpectedArgument(hint.getExpected()) || !looksLikeExpectedArgument(hint.getActual())) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
