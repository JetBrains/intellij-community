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
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.fixes.AddArgumentFix;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StringToUpperWithoutLocaleInspection extends BaseInspection {
  private static final CallMatcher MATCHER = CallMatcher.instanceCall(
    CommonClassNames.JAVA_LANG_STRING, HardcodedMethodConstants.TO_UPPER_CASE, HardcodedMethodConstants.TO_LOWER_CASE
  ).parameterCount(0);

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "StringToUpperCaseOrToLowerCaseWithoutLocale";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "string.touppercase.tolowercase.without.locale.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "string.touppercase.tolowercase.without.locale.problem.descriptor");
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiReferenceExpression methodExpression = (PsiReferenceExpression)infos[0];
    List<InspectionGadgetsFix> fixes = new ArrayList<>(2);
    final PsiModifierListOwner annotatableQualifier = NonNlsUtils.getAnnotatableQualifier(methodExpression);
    fixes.add(new AddArgumentFix("java.util.Locale.ENGLISH", "Locale.ENGLISH"));
    if (annotatableQualifier != null) {
      fixes.add(new DelegatingFix(new AddAnnotationPsiFix(
        AnnotationUtil.NON_NLS, annotatableQualifier,PsiNameValuePair.EMPTY_ARRAY)));
    }
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringToUpperWithoutLocaleVisitor();
  }

  private static class StringToUpperWithoutLocaleVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (!MATCHER.test(expression)) return;
      final PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
      if (NonNlsUtils.isNonNlsAnnotatedUse(expression) || NonNlsUtils.isNonNlsAnnotated(qualifier)) return;
      registerMethodCallError(expression, expression.getMethodExpression());
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      if (!MATCHER.methodReferenceMatches(expression)) return;
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (NonNlsUtils.isNonNlsAnnotatedUse(qualifier) || NonNlsUtils.isNonNlsAnnotated(qualifier)) return;
      PsiElement nameElement = Objects.requireNonNull(expression.getReferenceNameElement());
      registerError(nameElement, expression);
    }
  }
}