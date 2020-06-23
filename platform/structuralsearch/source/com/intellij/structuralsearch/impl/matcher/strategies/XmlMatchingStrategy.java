// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;

public class XmlMatchingStrategy implements MatchingStrategy {

  private final Language myLanguage;

  public XmlMatchingStrategy(Language language) {
    myLanguage = language;
  }

  @Override
  public boolean continueMatching(final PsiElement start) {
    // XmlElements can also appear under languages which are not a kind of XMLLanguage e.g. TypeScript JSX
    // assume here that when the user specifies to search for generic XML, these elements should also be found
    if (myLanguage != XMLLanguage.INSTANCE && !start.getLanguage().isKindOf(myLanguage)) {
      return false;
    }
    return start instanceof XmlElement;
  }

  @Override
  public boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
    return false;
  }
}
