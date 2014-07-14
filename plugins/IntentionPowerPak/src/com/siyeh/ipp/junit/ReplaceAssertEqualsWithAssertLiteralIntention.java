/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceAssertEqualsWithAssertLiteralIntention extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] args = argumentList.getExpressions();
    final String assertString;
    if (args.length == 2) {
      final String argText = args[0].getText();
      assertString = getAssertString(argText);
    }
    else {
      final String argText = args[1].getText();
      assertString = getAssertString(argText);
    }
    return IntentionPowerPackBundle.message("replace.assert.equals.with.assert.literal.intention.name", assertString);
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new AssertEqualsWithLiteralPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiReferenceExpression expression = call.getMethodExpression();
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] args = argumentList.getExpressions();
    final String assertString;
    final String actualArgumentText;
    if (args.length == 2) {
      @NonNls final String argText = args[0].getText();
      final PsiExpression otherArg;
      if ("true".equals(argText) || "false".equals(argText) || "null".equals(argText)) {
        otherArg = args[1];
      }
      else {
        otherArg = args[0];
      }
      actualArgumentText = otherArg.getText();
      assertString = getAssertString(argText);
    }
    else {
      @NonNls final String argText = args[1].getText();
      final PsiExpression otherArg;
      if ("true".equals(argText) || "false".equals(argText) || "null".equals(argText)) {
        otherArg = args[2];
      }
      else {
        otherArg = args[1];
      }
      actualArgumentText = args[0].getText() + ", " + otherArg.getText();
      assertString = getAssertString(argText);
    }
    final PsiElement qualifier = expression.getQualifier();
    @NonNls final StringBuilder newExpression = new StringBuilder();
    if (qualifier == null) {
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (!InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") &&
          !ImportUtils.addStaticImport("org.junit.Assert", assertString, element)) {
        newExpression.append("org.junit.Assert.");
      }
    }
    else {
      newExpression.append(qualifier.getText()).append('.');
    }
    newExpression.append(assertString).append('(').append(actualArgumentText).append(')');
    PsiReplacementUtil.replaceExpression(call, newExpression.toString());
  }

  @NonNls
  private static String getAssertString(@NonNls String text) {
    if ("true".equals(text)) {
      return "assertTrue";
    }
    if ("false".equals(text)) {
      return "assertFalse";
    }
    return "assertNull";
  }
}
