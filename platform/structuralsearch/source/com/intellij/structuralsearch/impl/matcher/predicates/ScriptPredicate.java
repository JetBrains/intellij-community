// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import groovy.lang.Script;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class ScriptPredicate extends MatchPredicate {
  private final ScriptSupport scriptSupport;

  public ScriptPredicate(Project project, String name, Script script, Set<String> variableNames) {
    scriptSupport = new ScriptSupport(project, script, name, variableNames);
  }

  @Override
  public boolean match(@NotNull PsiElement match, int start, int end, @NotNull MatchContext context) {
    return Boolean.TRUE.equals(scriptSupport.evaluate(context.hasResult() ? context.getResult() : null, match));
  }
}