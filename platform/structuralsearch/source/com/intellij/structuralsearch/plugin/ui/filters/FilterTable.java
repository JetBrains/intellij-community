// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.StructuralSearchProfile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public interface FilterTable {

  void addFilter(FilterAction filter);

  void removeFilter(FilterAction filter);

  NamedScriptableDefinition getVariable();

  Runnable getConstraintChangedCallback();

  @NotNull
  StructuralSearchProfile getProfile();

  @NotNull
  Project getProject();
}
