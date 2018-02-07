/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StringConstructorInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignoreSubstringArguments = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "string.constructor.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "RedundantStringConstructorCall";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "string.constructor.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "string.constructor.substring.parameter.option"), this,
                                          "ignoreSubstringArguments");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConstructorVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final Boolean noArguments = (Boolean)infos[0];
    return new StringConstructorFix(noArguments.booleanValue());
  }

  private static class StringConstructorFix extends InspectionGadgetsFix {

    private final String m_name;

    private StringConstructorFix(boolean noArguments) {
      if (noArguments) {
        m_name = InspectionGadgetsBundle.message(
          "string.constructor.replace.empty.quickfix");
      }
      else {
        m_name = InspectionGadgetsBundle.message(
          "string.constructor.replace.arg.quickfix");
      }
    }

    @Override
    @NotNull
    public String getName() {
      return m_name;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiNewExpression expression = (PsiNewExpression)descriptor.getPsiElement();
      final PsiExpressionList argList = expression.getArgumentList();
      assert argList != null;
      final PsiExpression[] args = argList.getExpressions();
      CommentTracker commentTracker = new CommentTracker();
      final String argText = (args.length == 1) ? commentTracker.text(args[0]) : "\"\"";

      PsiReplacementUtil.replaceExpression(expression, argText, commentTracker);
    }
  }

  private class StringConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(
      @NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (!TypeUtils.isJavaLangString(type)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 1) {
        return;
      }
      if (arguments.length == 1) {
        final PsiExpression argument = arguments[0];
        final PsiType parameterType = argument.getType();
        if (!TypeUtils.isJavaLangString(parameterType)) {
          return;
        }
        if (ignoreSubstringArguments &&
            hasSubstringArgument(argument)) {
          return;
        }
      }
      registerError(expression, Boolean.valueOf(arguments.length == 0));
    }

    private boolean hasSubstringArgument(PsiExpression argument) {
      if (!(argument instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)argument;
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiElement element = methodExpression.resolve();
      if (!(element instanceof PsiMethod)) {
        return false;
      }
      final PsiMethod method = (PsiMethod)element;
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return true;
      }
      final String className = aClass.getQualifiedName();
      @NonNls final String methodName = method.getName();
      return CommonClassNames.JAVA_LANG_STRING.equals(className) &&
             methodName.equals("substring");
    }
  }
}