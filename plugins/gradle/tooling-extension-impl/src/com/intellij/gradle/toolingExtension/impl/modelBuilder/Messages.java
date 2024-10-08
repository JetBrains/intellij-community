// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class Messages {

  // @formatter:off
  public final static @NotNull String PROJECT_MODEL_GROUP = "gradle.projectModel.group";

  public final static @NotNull String SCALA_PROJECT_MODEL_GROUP = "gradle.scalaProjectModel.group";

  public final static @NotNull String TASK_WARM_UP_GROUP = "gradle.taskWarmUp.group";
  public final static @NotNull String TASK_MODEL_GROUP = "gradle.taskModel.group";

  public final static @NotNull String SOURCE_SET_MODEL_GROUP = "gradle.sourceSetModel.group";
  public final static @NotNull String SOURCE_SET_MODEL_PROJECT_TASK_ARTIFACT_GROUP = "gradle.sourceSetModel.projectArtifact.group";
  public final static @NotNull String SOURCE_SET_MODEL_SKIPPED_PROJECT_TASK_ARTIFACT_GROUP = "gradle.sourceSetModel.projectArtifact.skipped.group";
  public final static @NotNull String SOURCE_SET_MODEL_NON_SOURCE_SET_ARTIFACT_GROUP = "gradle.sourceSetModel.nonSourceSetArtifact.group";
  public final static @NotNull String SOURCE_SET_MODEL_SKIPPED_NON_SOURCE_SET_ARTIFACT_GROUP = "gradle.sourceSetModel.nonSourceSetArtifact.skipped.group";
  public final static @NotNull String SOURCE_SET_MODEL_PROJECT_CONFIGURATION_ARTIFACT_GROUP = "gradle.sourceSetModel.projectConfigurationArtifact.group";
  public final static @NotNull String SOURCE_SET_MODEL_SKIPPED_PROJECT_CONFIGURATION_ARTIFACT_GROUP = "gradle.sourceSetModel.projectConfigurationArtifact.skipped.group";
  public final static @NotNull String SOURCE_SET_MODEL_SOURCE_SET_ARTIFACT_GROUP = "gradle.sourceSetModel.sourceSetArtifact.group";
  public final static @NotNull String SOURCE_SET_MODEL_SKIPPED_SOURCE_SET_ARTIFACT_GROUP = "gradle.sourceSetModel.sourceSetArtifact.skipped.group";

  public final static @NotNull String RESOURCE_FILTER_MODEL_GROUP = "gradle.resourceModel.group";

  public final static @NotNull String SOURCE_SET_ARTIFACT_INDEX_GROUP = "gradle.sourceSetArtifactIndex.group";
  public final static @NotNull String SOURCE_SET_ARTIFACT_INDEX_CACHE_SET_GROUP = "gradle.sourceSetArtifactIndex.cacheSet.group";
  public final static @NotNull String SOURCE_SET_DEPENDENCY_MODEL_GROUP = "gradle.sourceSetDependencyModel.group";

  public final static @NotNull String EAR_CONFIGURATION_MODEL_GROUP = "gradle.earConfigurationModel.group";
  public final static @NotNull String WAR_CONFIGURATION_MODEL_GROUP = "gradle.warConfigurationModel.group";

  public final static @NotNull String DEPENDENCY_DOWNLOAD_POLICY_MODEL_GROUP = "gradle.dependencyDownloadPolicyModel.group";
  public final static @NotNull String DEPENDENCY_DOWNLOAD_POLICY_MODEL_CACHE_GET_GROUP = "gradle.dependencyDownloadPolicyModel.cacheGet.group";
  public final static @NotNull String DEPENDENCY_DOWNLOAD_POLICY_MODEL_CACHE_SET_GROUP = "gradle.dependencyDownloadPolicyModel.cacheSet.group";

  public final static @NotNull String DEPENDENCY_CLASSPATH_MODEL_GROUP = "gradle.dependencyCompileClasspathModel.group";

  public final static @NotNull String DEPENDENCY_ACCESSOR_MODEL_GROUP = "gradle.dependencyAccessorModel.group";
  public final static @NotNull String DEPENDENCY_GRAPH_MODEL_GROUP = "gradle.dependencyGraphModel.group";

  public final static @NotNull String INTELLIJ_SETTINGS_MODEL_GROUP = "gradle.intellijSettingsModel.group";
  public final static @NotNull String INTELLIJ_PROJECT_SETTINGS_MODEL_GROUP = "gradle.intellijProjectSettingsModel.group";

  public final static @NotNull String BUILDSCRIPT_CLASSPATH_MODEL_GROUP = "gradle.buildScriptClasspathModel.group";
  public final static @NotNull String BUILDSCRIPT_CLASSPATH_MODEL_CACHE_GET_GROUP = "gradle.buildScriptClasspathModel.cacheGet.group";
  public final static @NotNull String BUILDSCRIPT_CLASSPATH_MODEL_CACHE_SET_GROUP = "gradle.buildScriptClasspathModel.cacheSet.group";

  public final static @NotNull String TEST_MODEL_GROUP = "gradle.testModel.group";
  public final static @NotNull String MAVEN_REPOSITORY_MODEL_GROUP = "gradle.mavenRepositoryModel.group";
  public final static @NotNull String ANNOTATION_PROCESSOR_MODEL_GROUP = "gradle.annotationProcessorModel.group";
  public final static @NotNull String PROJECT_EXTENSION_MODEL_GROUP = "gradle.projectExtensionModel.group";
  public final static @NotNull String VERSION_CATALOG_MODEL_GROUP = "gradle.versionCatalogModel.group";
  // @formatter:on
}
