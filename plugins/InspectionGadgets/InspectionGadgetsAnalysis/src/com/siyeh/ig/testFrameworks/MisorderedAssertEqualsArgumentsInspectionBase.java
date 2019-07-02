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
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

      final AssertHint hint = createAssertHint(callExpression);
      if (hint == null) {
        return;
      }
      final PsiExpression expectedArgument = hint.getExpected(checkTestNG());
      final PsiExpression actualArgument = hint.getActual(checkTestNG());
      final PsiElement copy = expectedArgument.copy();
      expectedArgument.replace(actualArgument);
      actualArgument.replace(copy);
    }
  }

  AssertHint createAssertHint(@NotNull PsiMethodCallExpression expression) {
    return AssertHint.create(expression, methodName -> methodNames.contains(methodName) ? 2 : null, checkTestNG());
  }

  static boolean isOnlyLibraryCodeUsed(PsiExpression expression) {
    if (expression == null) {
      return false;
    }

    final Ref<Boolean> libraryCode = Ref.create(Boolean.TRUE);
    final List<PsiExpression> expressions = new SmartList<>();
    expressions.add(expression);
    while (!expressions.isEmpty()) {
      expressions.remove(expressions.size() - 1).accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
          if (!libraryCode.get().booleanValue()) {
            return;
          }
          super.visitReferenceExpression(referenceExpression);
          final PsiElement target = referenceExpression.resolve();
          if (target instanceof PsiLocalVariable) {
            final PsiVariable variable = (PsiLocalVariable)target;
            final PsiExpression definition = DeclarationSearchUtils.findDefinition(referenceExpression, variable);
            if (definition == null) {
              libraryCode.set(Boolean.FALSE);
            }
            else {
              expressions.add(definition);
            }
          }
          else if (!(target instanceof PsiCompiledElement)) {
            libraryCode.set(Boolean.FALSE);
          }
        }
      });
    }
    return libraryCode.get().booleanValue();
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
      if (looksLikeExpectedArgument(hint.getExpected(checkTestNG())) || !looksLikeExpectedArgument(hint.getActual(checkTestNG()))) {
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
      if (PsiUtil.isConstantExpression(expression) || PsiType.NULL.equals(type)) {
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
          final PsiCodeBlock block = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
          if (block == null) {
            return false;
          }
          final PsiExpression definition = DeclarationSearchUtils.findDefinition(referenceExpression, variable);
          if (definition == null) {
            return false;
          }
          if (PsiUtil.isConstantExpression(definition) || PsiType.NULL.equals(definition.getType())) {
            return true;
          }
          final PsiElement[] refs = DefUseUtil.getRefs(block, variable, definition);
          final int offset = referenceExpression.getTextOffset();
          for (PsiElement ref : refs) {
            if (ref.getTextOffset() < offset) {
              return false;
            }
          }
          if (isOnlyLibraryCodeUsed(definition)) {
            return true;
          }
        }
      }
      else if (expression instanceof PsiCallExpression && type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass aClass = classType.resolve();
        if (aClass instanceof PsiCompiledElement) {
            return isOnlyLibraryCodeUsed(expression);
        }
      }
      return false;
    }
  }
}
