// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.psiutils;

import com.intellij.psi.PsiElement;
import com.siyeh.ig.psiutils.HighlightUtils;
import org.jetbrains.annotations.NotNull;

public final class HighlightUtil {

  private HighlightUtil() {}

  /**
   * Used in an external plugin "Parameter Formatter Plugin 1.1".
   * @deprecated Use {@link HighlightUtils#highlightElement(PsiElement, String)} instead
   */
  @Deprecated
  public static void highlightElement(@NotNull PsiElement element, @NotNull final String statusBarText) {
    HighlightUtils.highlightElement(element, statusBarText);
  }
}