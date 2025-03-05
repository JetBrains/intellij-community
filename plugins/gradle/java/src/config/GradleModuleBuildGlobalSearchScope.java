// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public final class GradleModuleBuildGlobalSearchScope extends DelegatingGlobalSearchScope {
  private final @NotNull String externalModulePath;

  public GradleModuleBuildGlobalSearchScope(@NotNull Project project,
                                            @NotNull GlobalSearchScope baseScope,
                                            @NotNull String externalModulePath) {
    super(new DelegatingGlobalSearchScope(project, baseScope));
    this.externalModulePath = externalModulePath;
  }

  public @NotNull String getExternalModulePath() {
    return externalModulePath;
  }
}
