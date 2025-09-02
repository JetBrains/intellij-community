// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

import java.util.List;

public final class GrFocusModeProvider implements FocusModeProvider {
  @Override
  public @NotNull List<? extends Segment> calcFocusZones(@NotNull PsiFile psiFile) {
    return SyntaxTraverser.psiTraverser(psiFile)
      .postOrderDfsTraversal()
      .filter(e -> e instanceof PsiClass || e instanceof PsiMethod)
      .filter(e -> {
        PsiElement parent = e.getParent();
        if (!(parent instanceof GrVariableDeclarationOwner)) return false;
        PsiElement grand = parent.getParent();
        return grand instanceof PsiClass && !(grand instanceof PsiAnonymousClass);
      })
      .map(e -> e.getTextRange()).toList();
  }
}
