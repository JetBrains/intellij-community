// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class AuxiliaryArtifactResolver {

  protected final @NotNull Project project;
  protected final @NotNull GradleDependencyDownloadPolicy policy;

  protected AuxiliaryArtifactResolver(@NotNull Project project, @NotNull GradleDependencyDownloadPolicy policy) {
    this.project = project;
    this.policy = policy;
  }

  @NotNull
  public abstract AuxiliaryConfigurationArtifacts resolve(@NotNull Configuration configuration);
}
