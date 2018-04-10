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
package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceAssertLiteralWithAssertEqualsIntention extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    assert methodName != null;
    final String postfix = methodName.substring("assert".length());
    final PsiExpression lastArgument = arguments[arguments.length - 1];
    if (lastArgument instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)lastArgument;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (("assertTrue".equals(methodName) && JavaTokenType.EQEQ.equals(tokenType)) ||
          ("assertFalse".equals(methodName) && JavaTokenType.NE.equals(tokenType))) {
        return IntentionPowerPackBundle.message("replace.assert.literal.with.assert.equals.intention.name2", methodName);
      }
    }
    final String literal = postfix.toLowerCase();
    if (arguments.length == 1) {
      return IntentionPowerPackBundle.message("replace.assert.literal.with.assert.equals.intention.name", methodName, literal);
    }
    else {
      return IntentionPowerPackBundle.message("replace.assert.literal.with.assert.equals.intention.name1", methodName, literal);
    }
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new AssertLiteralPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (methodName == null) {
      return;
    }
    @NonNls final StringBuilder newExpression = new StringBuilder();
    final PsiElement qualifier = methodExpression.getQualifier();
    if (qualifier == null) {
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (!InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") &&
          !ImportUtils.addStaticImport("org.junit.Assert", "assertEquals", element)) {
        newExpression.append("org.junit.Assert.");
      }
    }
    else {
      newExpression.append(qualifier.getText());
      newExpression.append('.');
    }
    newExpression.append("assertEquals(");
    final String postfix = methodName.substring("assert".length());
    final String literal = postfix.toLowerCase();
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    CommentTracker commentTracker = new CommentTracker();
    if (arguments.length > 1) {
      newExpression.append(commentTracker.text(arguments[0])).append(", ");
    }
    final PsiExpression lastArgument = arguments[arguments.length - 1];
    if (lastArgument instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)lastArgument;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (("assertTrue".equals(methodName) && JavaTokenType.EQEQ.equals(tokenType)) ||
          ("assertFalse".equals(methodName) && JavaTokenType.NE.equals(tokenType))) {
        final PsiExpression lhs = binaryExpression.getLOperand();
        newExpression.append(commentTracker.text(lhs)).append(", ");
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs != null) {
          newExpression.append(commentTracker.text(rhs));
        }
      }
      else {
        newExpression.append(literal).append(", ").append(commentTracker.text(lastArgument));
      }
    }
    else {
      newExpression.append(literal).append(", ").append(commentTracker.text(lastArgument));
    }
    newExpression.append(')');

    PsiReplacementUtil.replaceExpression(call, newExpression.toString(), commentTracker);
  }
}