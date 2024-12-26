// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal.ear;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.ear.EarConfiguration;

import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class EarConfigurationImpl implements EarConfiguration {

  private final @NotNull List<? extends EarModel> myEarModels;
  private final @NotNull Collection<ExternalDependency> myDeployDependencies;
  private final @NotNull Collection<ExternalDependency> myEarlibDependencies;

  public EarConfigurationImpl(@NotNull List<? extends EarModel> earModels,
                              @NotNull Collection<ExternalDependency> deployDependencies,
                              @NotNull Collection<ExternalDependency> earlibDependencies) {
    myEarModels = earModels;
    myDeployDependencies = deployDependencies;
    myEarlibDependencies = earlibDependencies;
  }

  @Override
  public List<? extends EarConfiguration.EarModel> getEarModels() {
    return myEarModels;
  }

  @Override
  public @NotNull Collection<ExternalDependency> getDeployDependencies() {
    return myDeployDependencies;
  }

  @Override
  public @NotNull Collection<ExternalDependency> getEarlibDependencies() {
    return myEarlibDependencies;
  }
}
