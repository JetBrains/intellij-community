// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;
import org.jetbrains.plugins.gradle.settings.TestRunner;

public abstract class GradleSettingsListenerAdapter implements ExternalSystemSettingsListener<GradleProjectSettings>,
                                                               GradleSettingsListener {
  @Override
  public void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath, @NotNull String linkedProjectPath) {
  }

  @Override
  public void onGradleDistributionTypeChange(DistributionType currentValue, @NotNull String linkedProjectPath) {
  }

  @Override
  public void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath) {
  }

  @Override
  public void onGradleVmOptionsChange(@Nullable String oldOptions, @Nullable String newOptions) {
  }

  @Override
  public void onBuildDelegationChange(boolean delegatedBuild, @NotNull String linkedProjectPath) {
  }

  @Override
  public void onTestRunnerChange(@NotNull TestRunner currentTestRunner, @NotNull String linkedProjectPath) {
  }
}
