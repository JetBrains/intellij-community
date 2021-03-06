// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.XmlUtil;

public final class TagValueFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new TagValueFilter();

  private TagValueFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof XmlTagChild || XmlUtil.isXmlToken(element, XmlTokenType.XML_DATA_CHARACTERS);
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
