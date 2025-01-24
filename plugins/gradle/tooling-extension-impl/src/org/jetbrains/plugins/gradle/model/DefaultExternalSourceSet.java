// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public final class DefaultExternalSourceSet implements ExternalSourceSet {

  private static final long serialVersionUID = 3L;

  private String name;

  private boolean isPreview;
  private File javaToolchainHome;
  private String sourceCompatibility;
  private String targetCompatibility;

  private Collection<File> artifacts;
  private @NotNull Collection<ExternalDependency> dependencies;
  private @NotNull Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> sources;

  public DefaultExternalSourceSet() {
    // Collection can be modified outside by mutation methods
    artifacts = new ArrayList<>(0);
    dependencies = new LinkedHashSet<>(0);
    sources = new HashMap<>(0);
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
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
  public @Nullable File getJavaToolchainHome() {
    return javaToolchainHome;
  }

  public void setJavaToolchainHome(@Nullable File javaToolchainHome) {
    this.javaToolchainHome = javaToolchainHome;
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

}
