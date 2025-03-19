// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.editor;

import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public final class GroovyTypeDeclarationProvider implements TypeDeclarationProvider {
  @Override
  public PsiElement @Nullable [] getSymbolTypeDeclarations(final @NotNull PsiElement targetElement) {
    PsiType type;
    if (targetElement instanceof GrVariable){
      type = ((GrVariable)targetElement).getTypeGroovy();
    }
    else if (targetElement instanceof GrMethod){
      type = ((GrMethod)targetElement).getInferredReturnType();
    }
    else {
      return null;
    }
    if (type == null) return null;
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass == null ? null : new PsiElement[] {psiClass};
  }
}
