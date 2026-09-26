// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.StructuralSearchScriptEngine;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Internal
public final class ScriptPredicate extends MatchPredicate {
  private final ScriptSupport scriptSupport;

  public ScriptPredicate(Project project, String name, StructuralSearchScriptEngine.CompiledScript script, Set<String> variableNames) {
    scriptSupport = new ScriptSupport(project, script, name, variableNames);
  }

  @Override
  public boolean match(@NotNull PsiElement match, int start, int end, @NotNull MatchContext context) {
    return Boolean.TRUE.equals(scriptSupport.evaluate(context.hasResult() ? context.getResult() : null, match));
  }
}