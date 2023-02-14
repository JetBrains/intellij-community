// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.interfacetoclass;

import com.intellij.codeInsight.daemon.impl.quickfix.ConvertInterfaceToClassFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.siyeh.ipp.base.PsiElementPredicate;

/**
 * @author Bas Leijdekkers
 */
class ConvertInterfaceToClassPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    final PsiClass aClass = ObjectUtils.tryCast(element.getParent(), PsiClass.class);
    if (aClass == null) return false;
    final PsiElement leftBrace = aClass.getLBrace();
    final int offsetInParent = element.getStartOffsetInParent();
    if (leftBrace == null || offsetInParent >= leftBrace.getStartOffsetInParent()) {
      return false;
    }
    return ConvertInterfaceToClassFix.canConvertToClass(aClass);
  }
}
