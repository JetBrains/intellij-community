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
import com.intellij.psi.tree.TokenSet;
import com.siyeh.ipp.base.PsiElementPredicate;

class OperatorAssignmentPredicate implements PsiElementPredicate {
  private static class Lazy {
    private static final TokenSet OPERATOR_ASSIGNMENT_TOKENS = TokenSet.create(
      JavaTokenType.PLUSEQ,
      JavaTokenType.MINUSEQ,
      JavaTokenType.ASTERISKEQ,
      JavaTokenType.PERCEQ,
      JavaTokenType.DIVEQ,
      JavaTokenType.ANDEQ,
      JavaTokenType.OREQ,
      JavaTokenType.XOREQ,
      JavaTokenType.LTLTEQ,
      JavaTokenType.GTGTEQ,
      JavaTokenType.GTGTGTEQ
    );
  }

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAssignmentExpression)) return false;
    PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
    IElementType tokenType = assignmentExpression.getOperationTokenType();
    return Lazy.OPERATOR_ASSIGNMENT_TOKENS.contains(tokenType);
  }
}