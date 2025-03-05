// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.byteCodeViewer;

import com.intellij.byteCodeViewer.ClassSearcher;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

public final class GroovyScriptClassSearcher implements ClassSearcher {
  @Override
  public @Nullable PsiClass findClass(@NotNull PsiElement place) {
    if (place.getLanguage() == GroovyLanguage.INSTANCE) {
      PsiClass containingClass = PsiTreeUtil.getParentOfType(place, PsiClass.class, false);
      while (containingClass instanceof PsiTypeParameter) {
        containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
      }
      if (containingClass != null) return containingClass;

      PsiFile file = place.getContainingFile();
      if (file instanceof GroovyFile && ((GroovyFile)file).isScript()) {
        return ((GroovyFile)file).getScriptClass();
      }
    }
    return null;
  }
}
