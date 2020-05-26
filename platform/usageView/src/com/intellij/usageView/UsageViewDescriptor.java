// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usageView;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TODO: Document
 */
public interface UsageViewDescriptor {
  /**
   * @return an array of elements whose usages were searched or {@link PsiElement#EMPTY_ARRAY} if not available
   */
  PsiElement @NotNull [] getElements();

  String getProcessedElementsHeader();

  @NotNull
  String getCodeReferencesText(int usagesCount, int filesCount);

  @Nullable
  default String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }
}