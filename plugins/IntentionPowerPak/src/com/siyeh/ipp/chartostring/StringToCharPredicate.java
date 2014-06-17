/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.chartostring;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

class StringToCharPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression expression =
      (PsiLiteralExpression)element;
    final PsiType type = expression.getType();
    if (type == null) {
      return false;
    }
    final String typeText = type.getCanonicalText();
    if (!JAVA_LANG_STRING.equals(typeText)) {
      return false;
    }
    final String value = (String)expression.getValue();
    if (value == null || value.length() != 1) {
      return false;
    }
    return isInConcatenationContext(element);
  }

  private static boolean isInConcatenationContext(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression parentExpression =
        (PsiPolyadicExpression)parent;
      final PsiType parentType = parentExpression.getType();
      if (parentType == null) {
        return false;
      }
      final String parentTypeText = parentType.getCanonicalText();
      if (!JAVA_LANG_STRING.equals(parentTypeText)) {
        return false;
      }
      if (parentExpression.getOperationTokenType() != JavaTokenType.PLUS) {
        return false;
      }
      final PsiExpression[] operands = parentExpression.getOperands();
      final int index = ArrayUtil.indexOf(operands, element);
      if (index > 0) {
        for (int i = 0; i < index && i < operands.length; i++) {
          final PsiType type = operands[i].getType();
          if (type != null && type.equalsToText(JAVA_LANG_STRING)) {
            return true;
          }
        }
      }
      else if (index == 0) {
        final PsiType type = operands[index + 1].getType();
        return type != null && type.equalsToText(JAVA_LANG_STRING);
      }
      return false;
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression parentExpression =
        (PsiAssignmentExpression)parent;
      final IElementType tokenType = parentExpression.getOperationTokenType();
      if (!JavaTokenType.PLUSEQ.equals(tokenType)) {
        return false;
      }
      final PsiType parentType = parentExpression.getType();
      if (parentType == null) {
        return false;
      }
      final String parentTypeText = parentType.getCanonicalText();
      return JAVA_LANG_STRING.equals(parentTypeText);
    }
    if (parent instanceof PsiExpressionList) {
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCall =
        (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression =
        methodCall.getMethodExpression();
      final PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      final PsiType type;
      if (qualifierExpression == null) {
        // to use the intention inside the source of
        // String and StringBuffer
        type = methodExpression.getType();
      }
      else {
        type = qualifierExpression.getType();
      }
      if (type == null) {
        return false;
      }
      final String className = type.getCanonicalText();
      if (CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className) ||
          CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(className)) {
        @NonNls final String methodName =
          methodExpression.getReferenceName();
        if (!"append".equals(methodName) &&
            !"insert".equals(methodName)) {
          return false;
        }
        final PsiElement method = methodExpression.resolve();
        return method != null;
      }
      else if (JAVA_LANG_STRING.equals(className)) {
        @NonNls final String methodName =
          methodExpression.getReferenceName();
        if (!"indexOf".equals(methodName) &&
            !"lastIndexOf".equals(methodName) &&
            !"replace".equals(methodName)) {
          return false;
        }
        final PsiElement method = methodExpression.resolve();
        return method != null;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
  }
}
