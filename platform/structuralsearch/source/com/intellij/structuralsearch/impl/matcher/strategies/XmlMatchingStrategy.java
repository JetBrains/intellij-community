// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;

public class XmlMatchingStrategy implements MatchingStrategy {

  private static final MatchingStrategy INSTANCE = new XmlMatchingStrategy();

  private XmlMatchingStrategy() {}

  @Override
  public boolean continueMatching(final PsiElement start) {
    return start instanceof XmlElement;
  }

  @Override
  public boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
    return false;
  }

  public static MatchingStrategy getInstance() {
    return INSTANCE;
  }
}
