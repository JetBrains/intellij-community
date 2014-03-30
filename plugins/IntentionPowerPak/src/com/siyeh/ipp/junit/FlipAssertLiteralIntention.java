/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FlipAssertLiteralIntention extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String fromMethodName = methodExpression.getReferenceName();
    @NonNls final String toMethodName;
    if ("assertTrue".equals(fromMethodName)) {
      toMethodName = "assertFalse";
    }
    else {
      toMethodName = "assertTrue";
    }
    return IntentionPowerPackBundle.message(
      "flip.assert.literal.intention.name",
      fromMethodName, toMethodName);
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new AssertTrueOrFalsePredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String fromMethodName = methodExpression.getReferenceName();
    @NonNls final String toMethodName;
    if ("assertTrue".equals(fromMethodName)) {
      toMethodName = "assertFalse";
    }
    else {
      toMethodName = "assertTrue";
    }
    @NonNls final StringBuilder newCall = new StringBuilder();
    final PsiElement qualifier = methodExpression.getQualifier();
    if (qualifier == null) {
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
      if (containingMethod != null && AnnotationUtil.isAnnotated(containingMethod, "org.junit.Test", true)) {
        if (!ImportUtils.addStaticImport("org.junit.Assert", toMethodName, element)) {
          newCall.append("org.junit.Assert.");
        }
      }
    }
    else {
      newCall.append(qualifier.getText()).append('.');
    }
    newCall.append(toMethodName).append('(');
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 1) {
      newCall.append(BoolUtils.getNegatedExpressionText(arguments[0]));
    }
    else {
      newCall.append(arguments[0].getText()).append(',');
      newCall.append(BoolUtils.getNegatedExpressionText(arguments[1]));
    }
    newCall.append(')');
    PsiReplacementUtil.replaceExpression(call, newCall.toString());
  }
}