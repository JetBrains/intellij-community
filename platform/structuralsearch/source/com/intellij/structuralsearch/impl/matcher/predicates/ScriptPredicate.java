// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class ScriptPredicate extends MatchPredicate {
  private final ScriptSupport scriptSupport;

  public ScriptPredicate(Project project, String name, String within, Set<String> variableNames, MatchOptions matchOptions) {
    scriptSupport = new ScriptSupport(project, within, name, variableNames, matchOptions);
  }

  @Override
  public boolean match(@NotNull PsiElement match, int start, int end, @NotNull MatchContext context) {
    return Boolean.TRUE.equals(scriptSupport.evaluate(context.hasResult() ? context.getResult() : null, match));
  }

}