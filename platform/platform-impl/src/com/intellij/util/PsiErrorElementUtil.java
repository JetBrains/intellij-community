/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

public class PsiErrorElementUtil {

  private static final Key<CachedValue<Boolean>> CONTAINS_ERROR_ELEMENT = Key.create("CONTAINS_ERROR_ELEMENT");

  private PsiErrorElementUtil() {}

  public static boolean hasErrors(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return ReadAction.compute(() -> {
      if (project.isDisposed() || !virtualFile.isValid()) return false;

      PsiManagerEx psiManager = PsiManagerEx.getInstanceEx(project);
      PsiFile psiFile = psiManager.getFileManager().findFile(virtualFile);
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
    HighlightErrorFilter[] filters = null;
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

  private static boolean shouldHighlightErrorElement(@NotNull PsiErrorElement error, @NotNull HighlightErrorFilter[] filters) {
    for (HighlightErrorFilter filter : filters) {
      if (!filter.shouldHighlightErrorElement(error)) {
        return false;
      }
    }
    return true;
  }
}
