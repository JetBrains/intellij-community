// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Default searching filter
 */
public final class DefaultFilter {
  public static boolean accepts(@NotNull PsiElement patternNode, @Nullable PsiElement matchNode) {
    if (patternNode instanceof LeafElement && matchNode instanceof LeafElement) {
      return ((LeafElement)patternNode).getElementType() == ((LeafElement)matchNode).getElementType();
    }
    return matchNode != null && patternNode.getClass() == matchNode.getClass();
  }
}
