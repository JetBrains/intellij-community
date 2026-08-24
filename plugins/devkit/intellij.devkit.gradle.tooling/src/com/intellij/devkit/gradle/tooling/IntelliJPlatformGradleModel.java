// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.gradle.tooling;

import org.gradle.tooling.model.Model;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Map;

/**
 * Transfers IntelliJ Platform dependency helpers and the generated product-release catalog from Gradle to the IDE.
 */
@ApiStatus.Internal
public interface IntelliJPlatformGradleModel extends Model, Serializable {
  @NotNull Map<String, String> getDependencyHelperProductCodes();

  @Nullable String getProductReleasesFile();

  @NotNull String getCurrentPluginVersion();

  @NotNull String getLatestPluginVersion();
}

final class IntelliJPlatformGradleModelImpl implements IntelliJPlatformGradleModel {
  private static final long serialVersionUID = 2L;

  private final @NotNull Map<String, String> dependencyHelperProductCodes;
  private final @Nullable String productReleasesFile;
  private final @NotNull String currentPluginVersion;
  private final @NotNull String latestPluginVersion;

  IntelliJPlatformGradleModelImpl(
    @NotNull Map<String, String> dependencyHelperProductCodes,
    @Nullable String productReleasesFile,
    @NotNull String currentPluginVersion,
    @NotNull String latestPluginVersion
  ) {
    this.dependencyHelperProductCodes = dependencyHelperProductCodes;
    this.productReleasesFile = productReleasesFile;
    this.currentPluginVersion = currentPluginVersion;
    this.latestPluginVersion = latestPluginVersion;
  }

  @Override
  public @NotNull Map<String, String> getDependencyHelperProductCodes() {
    return dependencyHelperProductCodes;
  }

  @Override
  public @Nullable String getProductReleasesFile() {
    return productReleasesFile;
  }

  @Override
  public @NotNull String getCurrentPluginVersion() {
    return currentPluginVersion;
  }

  @Override
  public @NotNull String getLatestPluginVersion() {
    return latestPluginVersion;
  }
}
