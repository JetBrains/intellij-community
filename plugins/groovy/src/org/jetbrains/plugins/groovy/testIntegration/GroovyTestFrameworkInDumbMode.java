// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.testIntegration;

import com.intellij.psi.PsiElement;
import com.intellij.testIntegration.JavaTestFrameworkInDumbMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

public class GroovyTestFrameworkInDumbMode implements JavaTestFrameworkInDumbMode {
  @Override
  public boolean supportDumbMode(@NotNull PsiElement psiElement) {
    return psiElement.getLanguage() == GroovyLanguage.INSTANCE;
  }
}
