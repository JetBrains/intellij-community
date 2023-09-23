// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceSet;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel;

import java.io.File;
import java.util.*;

@ApiStatus.Internal
public final class DefaultGradleSourceSetModel implements GradleSourceSetModel {

  private @Nullable String sourceCompatibility;
  private @Nullable String targetCompatibility;
  private @NotNull List<File> taskArtifacts;
  private @NotNull Map<String, Set<File>> configurationArtifacts;
  private @NotNull Map<String, DefaultExternalSourceSet> sourceSets;

  private @NotNull List<File> additionalArtifacts;

  public DefaultGradleSourceSetModel() {
    sourceCompatibility = null;
    targetCompatibility = null;
    taskArtifacts = new ArrayList<>();
    configurationArtifacts = new LinkedHashMap<>();
    sourceSets = new LinkedHashMap<>();
    additionalArtifacts = new ArrayList<>(0);
  }

  public DefaultGradleSourceSetModel(@NotNull GradleSourceSetModel sourceSetModel) {
    sourceCompatibility = sourceSetModel.getSourceCompatibility();
    targetCompatibility = sourceSetModel.getTargetCompatibility();
    taskArtifacts = sourceSetModel.getTaskArtifacts();
    configurationArtifacts = sourceSetModel.getConfigurationArtifacts();
    sourceSets = new LinkedHashMap<>();
    for (Map.Entry<String, ? extends ExternalSourceSet> entry : sourceSetModel.getSourceSets().entrySet()) {
      sourceSets.put(entry.getKey(), new DefaultExternalSourceSet(entry.getValue()));
    }
    additionalArtifacts = new ArrayList<>(sourceSetModel.getAdditionalArtifacts());
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
  public @NotNull List<File> getTaskArtifacts() {
    return taskArtifacts;
  }

  public void setTaskArtifacts(@NotNull List<File> taskArtifacts) {
    this.taskArtifacts = taskArtifacts;
  }

  @Override
  public @NotNull Map<String, Set<File>> getConfigurationArtifacts() {
    return configurationArtifacts;
  }

  public void setConfigurationArtifacts(@NotNull Map<String, Set<File>> configurationArtifacts) {
    this.configurationArtifacts = configurationArtifacts;
  }

  @Override
  public @NotNull Map<String, DefaultExternalSourceSet> getSourceSets() {
    return sourceSets;
  }

  public void setSourceSets(@NotNull Map<String, DefaultExternalSourceSet> sourceSets) {
    this.sourceSets = sourceSets;
  }


  public void setAdditionalArtifacts(@NotNull List<File> additionalArtifacts) {
    this.additionalArtifacts = additionalArtifacts;
  }

  @Override
  public  @NotNull List<File> getAdditionalArtifacts() {
    return additionalArtifacts;
  }

}
