// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class ScriptPredicate extends MatchPredicate {
  private final ScriptSupport scriptSupport;

  public ScriptPredicate(Project project, String name, String within, Set<String> variableNames) {
    scriptSupport = new ScriptSupport(project, within, name, variableNames);
  }

  @Override
  public boolean match(PsiElement match, int start, int end, MatchContext context) {
    if (match == null) return false;

    return Boolean.TRUE.equals(Boolean.valueOf(scriptSupport.evaluate(context.hasResult() ? context.getResult() : null, match)));
  }

}