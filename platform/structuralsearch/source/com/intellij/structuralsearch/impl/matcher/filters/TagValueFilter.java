// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTagChild;

public class TagValueFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new TagValueFilter();

  private TagValueFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof XmlTagChild;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
