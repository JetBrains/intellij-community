package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * @author Maxim.Mossienko
 */
public class ContainsPredicate extends MatchPredicate {

  public ContainsPredicate(String name, String within) {
  }

  public boolean match(PsiElement match, int start, int end, MatchContext context) {
    return false;
  }
}