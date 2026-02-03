// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary;

import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface AuxiliaryArtifactResolver {

  @NotNull
  AuxiliaryConfigurationArtifacts resolve(@NotNull Configuration configuration);
}
