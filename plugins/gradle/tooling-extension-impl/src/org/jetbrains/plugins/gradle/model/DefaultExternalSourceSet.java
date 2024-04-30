// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public final class DefaultExternalSourceSet implements ExternalSourceSet {
  private static final long serialVersionUID = 2L;

  private String name;
  private boolean isPreview;
  private String sourceCompatibility;
  private String targetCompatibility;
  // The jdkInstallationPath exists only for migration.
  // Source sets are serialized with the project data nodes.
  private String jdkInstallationPath;
  private File javaToolchainHome;
  private Collection<File> artifacts;
  private @NotNull Collection<ExternalDependency> dependencies;
  private @NotNull Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> sources;

  public DefaultExternalSourceSet() {
    // Collection can be modified outside by mutation methods
    sources = new HashMap<>(0);
    dependencies = new LinkedHashSet<>(0);
    artifacts = new ArrayList<>(0);
  }

  public DefaultExternalSourceSet(ExternalSourceSet sourceSet) {
    name = sourceSet.getName();
    isPreview = sourceSet.isPreview();
    javaToolchainHome = sourceSet.getJavaToolchainHome();
    sourceCompatibility = sourceSet.getSourceCompatibility();
    targetCompatibility = sourceSet.getTargetCompatibility();
    artifacts = copyArtifacts(sourceSet.getArtifacts());
    dependencies = ModelFactory.createCopy(sourceSet.getDependencies());
    sources = copySources(sourceSet.getSources());
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean isPreview() {
    return isPreview;
  }

  public void setPreview(boolean preview) {
    isPreview = preview;
  }

  @Override
  public @Nullable String getSourceCompatibility() {
    return sourceCompatibility;
  }

  public void setSourceCompatibility(@Nullable String sourceCompatibility) {
    this.sourceCompatibility = sourceCompatibility;
  }

  @Override
  public @Nullable String getTargetCompatibility() {
    return targetCompatibility;
  }

  public void setTargetCompatibility(@Nullable String targetCompatibility) {
    this.targetCompatibility = targetCompatibility;
  }

  @Override
  public @Nullable File getJavaToolchainHome() {
    if (jdkInstallationPath != null) {
      javaToolchainHome = new File(jdkInstallationPath);
      jdkInstallationPath = null;
    }
    return javaToolchainHome;
  }

  public void setJavaToolchainHome(@Nullable File javaToolchainHome) {
    this.jdkInstallationPath = null;
    this.javaToolchainHome = javaToolchainHome;
  }

  @Override
  public Collection<File> getArtifacts() {
    return artifacts;
  }

  public void setArtifacts(Collection<File> artifacts) {
    this.artifacts = artifacts;
  }

  @Override
  public @NotNull Collection<ExternalDependency> getDependencies() {
    return dependencies;
  }

  public void setDependencies(@NotNull Collection<ExternalDependency> dependencies) {
    this.dependencies = dependencies;
  }

  @Override
  public @NotNull Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> getSources() {
    return sources;
  }

  public void setSources(@NotNull Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> sources) {
    this.sources = new LinkedHashMap<>(sources);
  }

  public void addSource(@NotNull ExternalSystemSourceType sourceType, @NotNull DefaultExternalSourceDirectorySet sourceDirectorySet) {
    this.sources.put(sourceType, sourceDirectorySet);
  }

  @Override
  public String toString() {
    return "sourceSet '" + name + '\'' ;
  }

  private static @NotNull Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> copySources(
    @Nullable Map<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> sources
  ) {
    if (sources == null) {
      // Collection can be modified outside by mutation methods
      return new LinkedHashMap<>(0);
    }
    Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> result = new LinkedHashMap<>(sources.size());
    for (Map.Entry<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> entry : sources.entrySet()) {
      ExternalSystemSourceType sourceType = ExternalSystemSourceType.from(entry.getKey());
      DefaultExternalSourceDirectorySet directorySet = new DefaultExternalSourceDirectorySet(entry.getValue());
      result.put(sourceType, directorySet);
    }
    return result;
  }

  private static @NotNull Collection<File> copyArtifacts(@Nullable Collection<File> artifacts) {
    if (artifacts == null) {
      // Collection can be modified outside by mutation methods
      return new ArrayList<>(0);
    }
    return new ArrayList<>(artifacts);
  }
}
