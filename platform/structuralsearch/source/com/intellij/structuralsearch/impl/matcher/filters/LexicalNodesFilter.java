// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;

/**
 * Filter for lexical nodes
 */
public final class LexicalNodesFilter implements NodeFilter {

  private LexicalNodesFilter() {}

  public static NodeFilter getInstance() {
    return NodeFilterHolder.INSTANCE;
  }

  private static final class NodeFilterHolder {
    static final NodeFilter INSTANCE = new LexicalNodesFilter();
  }

  @Override
  public boolean accepts(PsiElement element) {
    if (element == null) return false;
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
    return profile != null && !profile.isMatchNode(element);
  }
}
