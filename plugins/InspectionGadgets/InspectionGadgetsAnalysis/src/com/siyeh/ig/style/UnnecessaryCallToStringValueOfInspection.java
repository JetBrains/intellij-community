/*
 * Copyright 2008-2018 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryCallToStringValueOfInspection extends BaseInspection implements CleanupLocalInspectionTool{

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.call.to.string.valueof.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final String text = (String)infos[0];
    return InspectionGadgetsBundle.message("unnecessary.call.to.string.valueof.problem.descriptor", text);
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final String text = (String)infos[0];
    return new UnnecessaryCallToStringValueOfFix(text);
  }

  public static String calculateReplacementText(PsiExpression expression) {
    if (!(expression instanceof PsiPolyadicExpression)) {
      return expression.getText();
    }
    final PsiType type = expression.getType();
    if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, type) ||
        ParenthesesUtils.getPrecedence(expression) < ParenthesesUtils.ADDITIVE_PRECEDENCE) {
      return expression.getText();
    }
    return '(' + expression.getText() + ')';
  }

  private static class UnnecessaryCallToStringValueOfFix extends InspectionGadgetsFix {

    private final String replacementText;

    UnnecessaryCallToStringValueOfFix(String replacementText) {
      this.replacementText = replacementText;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("unnecessary.call.to.string.valueof.quickfix", replacementText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)descriptor.getPsiElement();
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      CommentTracker tracker = new CommentTracker();
      PsiReplacementUtil.replaceExpression(methodCallExpression, calculateReplacementText(tracker.markUnchanged(arguments[0])), tracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryCallToStringValueOfVisitor();
  }

  private static class UnnecessaryCallToStringValueOfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String referenceName = methodExpression.getReferenceName();
      if (!"valueOf".equals(referenceName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = ParenthesesUtils.stripParentheses(arguments[0]);
      if (argument == null) {
        return;
      }
      final PsiType argumentType = argument.getType();
      if (argumentType instanceof PsiArrayType) {
        final PsiArrayType arrayType = (PsiArrayType)argumentType;
        final PsiType componentType = arrayType.getComponentType();
        if (PsiType.CHAR.equals(componentType)) {
          return;
        }
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String qualifiedName = aClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_STRING.equals(qualifiedName)) {
        return;
      }
      if (!TypeUtils.isJavaLangString(argumentType)) {
        final boolean throwable = TypeUtils.expressionHasTypeOrSubtype(argument, "java.lang.Throwable");
        if (ExpressionUtils.isConversionToStringNecessary(expression, throwable)) {
          return;
        }
      }
      if (argument instanceof PsiReferenceExpression) {
        if (couldChangeSemantics((PsiReferenceExpression)argument)) {
          return;
        }
      }
      else if (argument instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)argument;
        if (couldChangeSemantics(methodCallExpression.getMethodExpression())) {
          return;
        }
      }
      registerError(expression, calculateReplacementText(argument));
    }

    private static boolean couldChangeSemantics(PsiReferenceExpression referenceExpression) {
      final PsiElement target = referenceExpression.resolve();
      // unwrapping when null will change semantics
      return !(target instanceof PsiModifierListOwner && NullableNotNullManager.isNotNull((PsiModifierListOwner)target));
    }
  }
}
