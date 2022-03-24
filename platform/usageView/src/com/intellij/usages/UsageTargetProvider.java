// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this interface and register the implementation as {@code com.intellij.usageTargetProvider} extension in plugin.xml
 * to provide usage targets.
 *
 * @see com.intellij.find.usages.symbol.SymbolSearchTargetFactory
 */
public interface UsageTargetProvider {

  /**
   * @param editor currently opened editor
   * @param file   currently opened file in the {@code editor}
   * @return array of usage targets at the current editor offset in the file,
   * or {@code null} or an empty array if there are no usage targets at the current editor offset in the file
   */
  default UsageTarget @Nullable [] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
    return null;
  }

  /**
   * @param psiElement target element, for which usage target is requested
   * @return array of usage targets, which represent given target {@code psiElement}
   * or {@code null} or an empty array if the {@code psiElement} cannot be represented as a usage target by this provider
   */
  default UsageTarget @Nullable [] getTargets(@NotNull PsiElement psiElement) {
    return null;
  }
}
