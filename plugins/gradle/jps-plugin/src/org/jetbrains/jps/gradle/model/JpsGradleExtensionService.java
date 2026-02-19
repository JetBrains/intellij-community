// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.gradle.model.artifacts.JpsGradleArtifactExtension;
import org.jetbrains.jps.gradle.model.impl.GradleProjectConfiguration;
import org.jetbrains.jps.gradle.model.impl.artifacts.JpsGradleArtifactExtensionImpl;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author Vladislav.Soroka
 */
public abstract class JpsGradleExtensionService {
  public static @Nullable JpsGradleArtifactExtension getArtifactExtension(@NotNull JpsArtifact artifact) {
    return artifact.getContainer().getChild(JpsGradleArtifactExtensionImpl.ROLE);
  }

  public static JpsGradleExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsGradleExtensionService.class);
  }

  public abstract @Nullable JpsGradleModuleExtension getExtension(@NotNull JpsModule module);

  public abstract @NotNull JpsGradleModuleExtension getOrCreateExtension(@NotNull JpsModule module, @Nullable String moduleType);

  public abstract void setProductionOnTestDependency(@NotNull JpsDependencyElement dependency, boolean value);

  public abstract boolean isProductionOnTestDependency(@NotNull JpsDependencyElement dependency);

  public abstract boolean hasGradleProjectConfiguration(@NotNull BuildDataPaths paths);

  public abstract @NotNull GradleProjectConfiguration getGradleProjectConfiguration(BuildDataPaths paths);
}
