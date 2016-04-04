package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.*;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;

/**
 * Handles finding method
 */
public final class ReferencePredicate extends SubstitutionHandler {
  public ReferencePredicate(String _name) {
    super(_name, true, 1, 1, true);
  }

  public boolean match(PsiElement node, PsiElement match, MatchContext context) {
    match = StructuralSearchUtil.getParentIfIdentifier(match);

    PsiElement result = MatchUtils.getReferencedElement(match);
    if (result == null) {
      result = match;
      //return false;
    }

    return handle(result,context);
  }
}
