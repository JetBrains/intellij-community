// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.interfacetoclass;

import com.intellij.codeInsight.daemon.impl.quickfix.ConvertInterfaceToClassFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ConvertInterfaceToClassIntention extends Intention {

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiClass anInterface = (PsiClass)element.getParent();
    ConvertInterfaceToClassFix.convert(anInterface);
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new ConvertInterfaceToClassPredicate();
  }
}