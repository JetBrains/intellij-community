package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class ContainsPredicate extends MatchPredicate {

  public ContainsPredicate(String name, String within) {
  }

  @Override
  public boolean match(@NotNull PsiElement match, int start, int end, @NotNull MatchContext context) {
    return false;
  }
}