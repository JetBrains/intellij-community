// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PsiErrorElementUtil {
  private static final Key<CachedValue<Boolean>> CONTAINS_ERROR_ELEMENT = Key.create("CONTAINS_ERROR_ELEMENT");

  private PsiErrorElementUtil() {}

  public static boolean hasErrors(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return ReadAction.compute(() -> {
      if (project.isDisposed() || !virtualFile.isValid()) return false;
      PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      return psiFile != null && hasErrors(psiFile);
    });
  }

  private static boolean hasErrors(@NotNull PsiFile psiFile) {
    CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(psiFile.getProject());
    return cachedValuesManager.getCachedValue(
      psiFile, CONTAINS_ERROR_ELEMENT,
      () -> CachedValueProvider.Result.create(hasErrorElements(psiFile), psiFile),
      false
    );
  }

  private static boolean hasErrorElements(@NotNull PsiElement element) {
    List<HighlightErrorFilter> filters = null;
    for (PsiErrorElement error : SyntaxTraverser.psiTraverser(element).traverse().filter(PsiErrorElement.class)) {
      if (filters == null) {
        filters = HighlightErrorFilter.EP_NAME.getExtensions(element.getProject());
      }
      if (shouldHighlightErrorElement(error, filters)) {
        return true;
      }
    }
    return false;
  }

  private static boolean shouldHighlightErrorElement(@NotNull PsiErrorElement error, @NotNull List<HighlightErrorFilter> filters) {
    for (HighlightErrorFilter filter : filters) {
      if (!filter.shouldHighlightErrorElement(error)) {
        return false;
      }
    }
    return true;
  }
}
