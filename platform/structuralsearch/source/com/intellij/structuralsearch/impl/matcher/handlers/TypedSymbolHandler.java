package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import org.jetbrains.annotations.NotNull;

/**
 * Search handler for typed symbol ('T<a*>)
 */
public class TypedSymbolHandler extends MatchingHandler {
  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, @NotNull MatchContext context) {
    if (!super.match(patternNode,matchedNode,context)) {
      return false;
    }

    return context.getMatcher().match(
      patternNode.getFirstChild(),
      matchedNode
    );
  }
}
