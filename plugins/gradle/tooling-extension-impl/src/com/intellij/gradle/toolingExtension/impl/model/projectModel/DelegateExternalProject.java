// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.projectModel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.ExternalTask;
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public abstract class DelegateExternalProject implements ExternalProject {

  private final @NotNull ExternalProject externalProject;

  public DelegateExternalProject(@NotNull ExternalProject externalProject) {
    this.externalProject = externalProject;
  }

  @Override
  public @NotNull String getExternalSystemId() {
    return externalProject.getExternalSystemId();
  }

  @Override
  public @NotNull String getId() {
    return externalProject.getId();
  }

  @Override
  public @NotNull String getPath() {
    return externalProject.getPath();
  }

  @Override
  public @NotNull String getIdentityPath() {
    return externalProject.getIdentityPath();
  }

  @Override
  public @NotNull String getName() {
    return externalProject.getName();
  }

  @Override
  public @NotNull String getQName() {
    return externalProject.getQName();
  }

  @Override
  public @Nullable String getDescription() {
    return externalProject.getDescription();
  }

  @Override
  public @NotNull String getGroup() {
    return externalProject.getGroup();
  }

  @Override
  public @NotNull String getVersion() {
    return externalProject.getVersion();
  }

  @Override
  public @Nullable String getSourceCompatibility() {
    return externalProject.getSourceCompatibility();
  }

  @Override
  public @Nullable String getTargetCompatibility() {
    return externalProject.getTargetCompatibility();
  }

  @Override
  public @NotNull Map<String, ? extends ExternalProject> getChildProjects() {
    return externalProject.getChildProjects();
  }

  @Override
  public @NotNull File getProjectDir() {
    return externalProject.getProjectDir();
  }

  @Override
  public @NotNull File getBuildDir() {
    return externalProject.getBuildDir();
  }

  @Override
  public @Nullable File getBuildFile() {
    return externalProject.getBuildFile();
  }

  @Override
  public @NotNull Map<String, ? extends ExternalTask> getTasks() {
    return externalProject.getTasks();
  }

  @Override
  public @NotNull Map<String, ? extends ExternalSourceSet> getSourceSets() {
    return externalProject.getSourceSets();
  }

  @Override
  public @NotNull List<File> getArtifacts() {
    return externalProject.getArtifacts();
  }

  @Override
  public @NotNull Map<String, Set<File>> getArtifactsByConfiguration() {
    return externalProject.getArtifactsByConfiguration();
  }

  @Override
  public @NotNull GradleSourceSetModel getSourceSetModel() {
    return externalProject.getSourceSetModel();
  }
}
