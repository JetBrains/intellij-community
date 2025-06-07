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

  private @Nullable String name;

  private @Nullable File javaToolchainHome;
  private @Nullable String sourceCompatibility;
  private @Nullable String targetCompatibility;
  private @Nullable List<String> compilerArguments;

  private @NotNull Collection<File> artifacts;
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
    return Objects.requireNonNull(name, "The source set's name property has not been initialized");
  }

  public void setName(@NotNull String name) {
    this.name = name;
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
  public @NotNull List<String> getCompilerArguments() {
    return Collections.unmodifiableList(
      Objects.requireNonNull(compilerArguments, "The source set's compilerArguments property has not been initialized")
    );
  }

  public void setCompilerArguments(@NotNull List<String> compilerArguments) {
    this.compilerArguments = compilerArguments;
  }

  @Override
  public @NotNull Collection<File> getArtifacts() {
    return artifacts;
  }

  public void setArtifacts(@NotNull Collection<File> artifacts) {
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
