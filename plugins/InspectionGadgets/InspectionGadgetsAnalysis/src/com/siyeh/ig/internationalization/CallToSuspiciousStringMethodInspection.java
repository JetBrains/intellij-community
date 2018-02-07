/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class CallToSuspiciousStringMethodInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("call.to.suspicious.string.method.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("call.to.suspicious.string.method.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToSuspiciousStringMethodVisitor();
  }

  @Override
  @NotNull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)infos[0];
    final List<InspectionGadgetsFix> result = new ArrayList<>();
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiModifierListOwner annotatableQualifier = NonNlsUtils.getAnnotatableQualifier(methodExpression);
    if (annotatableQualifier != null) {
      final InspectionGadgetsFix fix =
        new DelegatingFix(new AddAnnotationPsiFix(AnnotationUtil.NON_NLS, annotatableQualifier, PsiNameValuePair.EMPTY_ARRAY));
      result.add(fix);
    }
    final PsiModifierListOwner annotatableArgument = NonNlsUtils.getAnnotatableArgument(methodCallExpression);
    if (annotatableArgument != null) {
      final InspectionGadgetsFix fix =
        new DelegatingFix(new AddAnnotationPsiFix(AnnotationUtil.NON_NLS, annotatableArgument, PsiNameValuePair.EMPTY_ARRAY));
      result.add(fix);
    }
    return result.toArray(new InspectionGadgetsFix[0]);
  }

  private static class CallToSuspiciousStringMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (!MethodCallUtils.isEqualsCall(expression) &&
          !MethodCallUtils.isEqualsIgnoreCaseCall(expression) &&
          !MethodCallUtils.isCompareToCall(expression) &&
          !MethodCallUtils.isCompareToIgnoreCaseCall(expression)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_STRING.equals(className)) {
        return;
      }

      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (NonNlsUtils.isNonNlsAnnotated(qualifier)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1 || NonNlsUtils.isNonNlsAnnotated(arguments[0])) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }
}
