// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.StructuralSearchProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public interface FilterTable {

  void addFilter(FilterAction filter);

  void removeFilter(FilterAction filter);

  NamedScriptableDefinition getVariable();

  default @Nullable MatchVariableConstraint getMatchVariable() {
    final NamedScriptableDefinition variable = getVariable();
    return variable instanceof MatchVariableConstraint ? (MatchVariableConstraint)variable : null;
  }

  Runnable getConstraintChangedCallback();

  @Nullable
  StructuralSearchProfile getProfile();

  @NotNull
  Project getProject();
}
