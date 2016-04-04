/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaToken;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class IfToSwitchPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken token = (PsiJavaToken)element;
    if (token.getTokenType() != JavaTokenType.IF_KEYWORD) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    final PsiIfStatement statement = (PsiIfStatement)parent;
    if (ErrorUtil.containsError(statement)) {
      return false;
    }
    return SwitchUtils.getSwitchExpression(statement, 0, false, true) != null;
  }
}