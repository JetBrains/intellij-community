/*
 * Copyright 2003-20185 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.util.ChangeToAppendUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class StringConcatenationInsideStringBufferAppendInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.inside.string.buffer.append.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    return InspectionGadgetsBundle.message("string.concatenation.inside.string.buffer.append.problem.descriptor", className);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInsideStringBufferAppendVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceWithChainedAppendFix();
  }

  private static class ReplaceWithChainedAppendFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "string.concatenation.inside.string.buffer.append.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement methodNameElement = descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)methodNameElement.getParent();
      if (methodExpression == null) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)methodExpression.getParent();
      if (methodCallExpression == null) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      CommentTracker ct = new CommentTracker();
      final PsiExpression argument = ct.markUnchanged(arguments[0]);
      final PsiExpression appendExpression = ChangeToAppendUtil.buildAppendExpression(qualifier, argument);
      if (appendExpression == null) {
        return;
      }
      ct.replaceAndRestoreComments(methodCallExpression, appendExpression);
    }
  }

  private static class StringConcatenationInsideStringBufferAppendVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"append".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      if (!ExpressionUtils.isConcatenation(ParenthesesUtils.stripParentheses(argument)) ||
          PsiUtil.isConstantExpression(argument)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className) ||
          CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(className)) {
        registerMethodCallError(expression, containingClass);
        return;
      }
      final Project project = containingClass.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass appendableClass = psiFacade.findClass("java.lang.Appendable", GlobalSearchScope.allScope(project));
      if (appendableClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritorOrSelf(containingClass, appendableClass, true)) {
        return;
      }
      registerMethodCallError(expression, containingClass);
    }
  }
}