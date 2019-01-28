// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

import java.util.List;

public class GrFocusModeProvider implements FocusModeProvider {
  @NotNull
  @Override
  public List<? extends Segment> calcFocusZones(@NotNull PsiFile file) {
    return SyntaxTraverser.psiTraverser(file)
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
