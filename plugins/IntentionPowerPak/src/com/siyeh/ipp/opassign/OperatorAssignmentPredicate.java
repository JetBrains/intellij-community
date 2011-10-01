/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ipp.opassign;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

import java.util.HashSet;
import java.util.Set;

class OperatorAssignmentPredicate implements PsiElementPredicate {

  private static final Set<IElementType> OPERATOR_ASSIGNMENT_TOKENS =
    new HashSet();

  static {
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.PLUSEQ);
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.MINUSEQ);
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.ASTERISKEQ);
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.PERCEQ);
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.DIVEQ);
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.ANDEQ);
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.OREQ);
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.XOREQ);
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.LTLTEQ);
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.GTGTEQ);
    OPERATOR_ASSIGNMENT_TOKENS.add(JavaTokenType.GTGTGTEQ);
  }

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAssignmentExpression)) {
      return false;
    }
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)element;
    final IElementType tokenType =
      assignmentExpression.getOperationTokenType();
    return OPERATOR_ASSIGNMENT_TOKENS.contains(tokenType);
  }
}