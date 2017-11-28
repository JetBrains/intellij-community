/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiSwitchStatement;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceSwitchWithIfIntention extends Intention {
  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new SwitchPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiJavaToken switchToken = (PsiJavaToken)element;
    final PsiSwitchStatement switchStatement = (PsiSwitchStatement)switchToken.getParent();
    if (switchStatement == null) {
      return;
    }
    ConvertSwitchToIfIntention.doProcessIntention(switchStatement);
  }

  public static boolean canProcess(@NotNull PsiSwitchStatement switchLabelStatement) {
    return SwitchPredicate.checkSwitchStatement(switchLabelStatement);
  }
}