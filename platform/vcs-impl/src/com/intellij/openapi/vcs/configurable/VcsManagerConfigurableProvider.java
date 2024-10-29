// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class VcsManagerConfigurableProvider extends ConfigurableProvider {
  @NotNull private final Project myProject;

  public VcsManagerConfigurableProvider(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Configurable createConfigurable() {
    return new VcsManagerConfigurable(myProject);
  }

  @Override
  public boolean canCreateConfigurable() {
    return !AllVcses.getInstance(myProject).isEmpty();
  }
}
