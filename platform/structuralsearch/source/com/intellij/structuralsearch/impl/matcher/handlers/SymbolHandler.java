// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * Search handler for symbol search
 */
public class SymbolHandler extends MatchingHandler {
  private final SubstitutionHandler handler;

  public SymbolHandler(SubstitutionHandler handler) {
    this.handler = handler;
  }

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return handler.handle(matchedNode, context);
  }
}
