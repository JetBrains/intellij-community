// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.GradleSourceSetDependencyModel;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@ApiStatus.Internal
public class DefaultGradleSourceSetDependencyModel implements GradleSourceSetDependencyModel {

  private @NotNull Map<String, Collection<ExternalDependency>> dependencies;

  public DefaultGradleSourceSetDependencyModel() {
    dependencies = new LinkedHashMap<>(0);
  }

  @Override
  public @NotNull Map<String, Collection<ExternalDependency>> getDependencies() {
    return dependencies;
  }

  public void setDependencies(@NotNull Map<String, Collection<ExternalDependency>> dependencies) {
    this.dependencies = dependencies;
  }
}
