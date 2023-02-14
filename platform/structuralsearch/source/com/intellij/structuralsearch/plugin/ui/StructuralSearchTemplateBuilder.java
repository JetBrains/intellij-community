// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is a temporary solution to extract Java-specific implementation from platform code. In order to implement creation of SSR templates
 * from code properly, we probably need to implement language-specific extensions.
 */
public class StructuralSearchTemplateBuilder {
  public static StructuralSearchTemplateBuilder getInstance() {
    return ApplicationManager.getApplication().getService(StructuralSearchTemplateBuilder.class);
  }

  public @Nullable TemplateBuilder buildTemplate(@NotNull PsiFile psiFile) {
    return null;
  }
}
