// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public class StructuralSearchScriptScope extends DelegatingGlobalSearchScope{
  public StructuralSearchScriptScope(@NotNull Project project) {
    super(GlobalSearchScope.everythingScope(project));
  }
}
