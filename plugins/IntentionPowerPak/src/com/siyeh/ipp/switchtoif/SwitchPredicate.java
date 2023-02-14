/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.switchtoif;

import com.intellij.codeInsight.daemon.impl.quickfix.ConvertSwitchToIfIntention;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;

class SwitchPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken token)) {
      return false;
    }
    final IElementType tokenType = token.getTokenType();
    if (!JavaTokenType.SWITCH_KEYWORD.equals(tokenType)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiSwitchStatement)) {
      return false;
    }
    return checkSwitchStatement((PsiSwitchStatement)parent);
  }

  public static boolean checkSwitchStatement(@NotNull PsiSwitchStatement switchStatement) {
    final PsiExpression expression = switchStatement.getExpression();
    if (expression == null) {
      return false;
    }
    final PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return false;
    }
    if (ErrorUtil.containsError(switchStatement)) {
      return false;
    }
    if (!ConvertSwitchToIfIntention.isAvailable(switchStatement)) {
      return false;
    }
    final PsiStatement[] statements = body.getStatements();
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiSwitchLabelStatementBase && !SwitchUtils.isDefaultLabel((PsiSwitchLabelStatementBase)statement)) {
        return true;
      }
    }
    return false;
  }
}