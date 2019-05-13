// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;

/**
 * Default searching filter
 */
public class DefaultFilter {
  public static boolean accepts(PsiElement patternNode, PsiElement matchNode) {
    if (patternNode instanceof LeafElement && matchNode instanceof LeafElement) {
      return ((LeafElement)patternNode).getElementType() == ((LeafElement)matchNode).getElementType();
    }
    return matchNode != null && patternNode.getClass()==matchNode.getClass();
  }
}
