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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class MergeIfOrPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    return isMergableExplicitIf(element) || isMergableImplicitIf(element);
  }

  public static boolean isMergableExplicitIf(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiElement parent = token.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    final PsiIfStatement ifStatement = (PsiIfStatement)parent;
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (thenBranch == null) {
      return false;
    }
    if (elseBranch == null) {
      return false;
    }
    if (!(elseBranch instanceof PsiIfStatement)) {
      return false;
    }
    if (ErrorUtil.containsError(ifStatement)) {
      return false;
    }
    final PsiIfStatement childIfStatement = (PsiIfStatement)elseBranch;
    final PsiStatement childThenBranch = childIfStatement.getThenBranch();
    return EquivalenceChecker.getCanonicalPsiEquivalence().statementsAreEquivalent(thenBranch, childThenBranch);
  }

  private static boolean isMergableImplicitIf(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken token = (PsiJavaToken)element;

    final PsiElement parent = token.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    final PsiIfStatement ifStatement = (PsiIfStatement)parent;
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch == null) {
      return false;
    }
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) {
      return false;
    }
    if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
      return false;
    }
    final PsiElement nextStatement = PsiTreeUtil.skipWhitespacesForward(ifStatement);
    if (!(nextStatement instanceof PsiIfStatement)) {
      return false;
    }
    final PsiIfStatement nextIfStatement = (PsiIfStatement)nextStatement;
    final PsiStatement nextThenBranch = nextIfStatement.getThenBranch();
    return EquivalenceChecker.getCanonicalPsiEquivalence().statementsAreEquivalent(thenBranch, nextThenBranch);
  }
}
