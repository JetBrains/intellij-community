// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class AutomaticResourceManagementPredicate
  implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken javaToken)) {
      return false;
    }
    final IElementType tokenType = javaToken.getTokenType();
    if (!JavaTokenType.TRY_KEYWORD.equals(tokenType)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiTryStatement tryStatement)) {
      return false;
    }
    if (tryStatement.getTryBlock() == null) {
      return false;
    }
    final PsiResourceList resourceList = tryStatement.getResourceList();
    return resourceList != null;
  }
}
