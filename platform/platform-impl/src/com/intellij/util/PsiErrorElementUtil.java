/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

public class PsiErrorElementUtil {

  private static final Key<CachedValue<Boolean>> CONTAINS_ERROR_ELEMENT = Key.create("CONTAINS_ERROR_ELEMENT");

  private PsiErrorElementUtil() {}

  public static boolean hasErrors(@NotNull final Project project, @NotNull final VirtualFile virtualFile) {
    if (project.isDisposed() || !virtualFile.isValid()) {
      return false;
    }
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (project.isDisposed()) {
          return false;
        }
        PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(project);
        PsiFile psiFile = psiManager.getFileManager().findFile(virtualFile);
        return psiFile != null && hasErrors(psiFile);
      }
    });
  }

  private static boolean hasErrors(@NotNull final PsiFile psiFile) {
    CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(psiFile.getProject());
    return cachedValuesManager.getCachedValue(
      psiFile,
      CONTAINS_ERROR_ELEMENT,
      new CachedValueProvider<Boolean>() {
        @Override
        public Result<Boolean> compute() {
          boolean error = hasErrorElements(psiFile);
          return Result.create(error, psiFile);
        }
      },
      false
    );
  }

  private static boolean hasErrorElements(@NotNull final PsiElement element) {
    if (element instanceof PsiErrorElement) {
      HighlightErrorFilter[] errorFilters = Extensions.getExtensions(HighlightErrorFilter.EP_NAME, element.getProject());
      for (HighlightErrorFilter errorFilter : errorFilters) {
        if (!errorFilter.shouldHighlightErrorElement((PsiErrorElement)element)) {
          return false;
        }
      }
      return true;
    }
    for (PsiElement child : element.getChildren()) {
      if (hasErrorElements(child)) {
        return true;
      }
    }
    return false;
  }
}
