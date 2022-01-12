// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui.modifier;

import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.StructuralSearchProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public interface ModifierTable {

  void addModifier(ModifierAction filter);

  void removeModifier(ModifierAction filter);

  NamedScriptableDefinition getVariable();

  @Nullable
  default MatchVariableConstraint getMatchVariable() {
    final NamedScriptableDefinition variable = getVariable();
    return variable instanceof MatchVariableConstraint ? (MatchVariableConstraint)variable : null;
  }

  Runnable getConstraintChangedCallback();

  @Nullable
  StructuralSearchProfile getProfile();

  @NotNull
  Project getProject();
}
