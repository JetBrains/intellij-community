// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class VcsManagerConfigurableProvider extends ConfigurableProvider {
  private final @NotNull Project myProject;

  public VcsManagerConfigurableProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull Configurable createConfigurable() {
    return new VcsManagerConfigurable(myProject);
  }

  @Override
  public boolean canCreateConfigurable() {
    return !AllVcses.getInstance(myProject).isEmpty();
  }
}
