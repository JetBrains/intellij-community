package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * @author Maxim.Mossienko
 */
public class ScriptPredicate extends AbstractStringBasedPredicate {
  private final ScriptSupport scriptSupport;

  public ScriptPredicate(Project project, String name, String within) {
    super(name, within);
    scriptSupport = new ScriptSupport(project, within, name);
  }

  @Override
  public boolean match(PsiElement node, PsiElement match, int start, int end, MatchContext context) {
    if (match == null) return false;

    return Boolean.TRUE.equals(
      Boolean.valueOf(scriptSupport.evaluate(
        context.hasResult() ? context.getResult() : null,
        match
      ))
    );
  }

}