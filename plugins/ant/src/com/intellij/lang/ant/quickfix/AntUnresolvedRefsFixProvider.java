// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.lang.ant.dom.AntDomReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.TagNameReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class AntUnresolvedRefsFixProvider extends UnresolvedReferenceQuickFixProvider<PsiReference> {

  @Override
  public void registerFixes(@NotNull PsiReference ref, @NotNull QuickFixActionRegistrar registrar) {
    if (ref instanceof TagNameReference || ref instanceof AntDomReference) {
      registrar.register(new AntChangeContextFix());
    }
  }

  @Override
  public @NotNull Class<PsiReference> getReferenceClass() {
    return PsiReference.class;
  }
}
