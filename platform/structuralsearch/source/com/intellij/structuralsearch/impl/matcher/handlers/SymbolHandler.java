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
    return handler.canMatch(patternNode, matchedNode) && handler.handle(matchedNode, context);
  }
}
