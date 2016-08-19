/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class StringCompareToInspection extends BaseInspection {

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "CallToStringCompareTo";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "string.compareto.call.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "string.compareto.call.problem.descriptor");
  }

  @Override
  @NotNull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiMethodCallExpression methodCallExpression =
      (PsiMethodCallExpression)infos[0];
    final List<InspectionGadgetsFix> result = new ArrayList<>();
    final PsiReferenceExpression methodExpression =
      methodCallExpression.getMethodExpression();
    final PsiModifierListOwner annotatableQualifier =
      NonNlsUtils.getAnnotatableQualifier(
        methodExpression);
    if (annotatableQualifier != null) {
      final InspectionGadgetsFix fix = new DelegatingFix(
        new AddAnnotationPsiFix(AnnotationUtil.NON_NLS,
                             annotatableQualifier,PsiNameValuePair.EMPTY_ARRAY));
      result.add(fix);
    }
    final PsiModifierListOwner annotatableArgument =
      NonNlsUtils.getAnnotatableArgument(
        methodCallExpression);
    if (annotatableArgument != null) {
      final InspectionGadgetsFix fix = new DelegatingFix(
        new AddAnnotationPsiFix(AnnotationUtil.NON_NLS,
                             annotatableArgument,PsiNameValuePair.EMPTY_ARRAY));
      result.add(fix);
    }
    return result.toArray(new InspectionGadgetsFix[result.size()]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringCompareToVisitor();
  }

  private static class StringCompareToVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isStringCompareTo(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (NonNlsUtils.isNonNlsAnnotated(qualifier)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      if (NonNlsUtils.isNonNlsAnnotated(arguments[0])) {
        return;
      }
      registerMethodCallError(expression, expression);
    }

    private static boolean isStringCompareTo(
      PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.COMPARE_TO.equals(name)) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      if (!MethodUtils.isCompareTo(method)) {
        return false;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType parameterType = parameters[0].getType();
      if (!TypeUtils.isJavaLangObject(parameterType) &&
          !TypeUtils.isJavaLangString(parameterType)) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String className = aClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_STRING.equals(className);
    }
  }
}