// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.util.UserDataHolderEx;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.Build;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
public interface ProjectResolverContext extends UserDataHolderEx {
  @NotNull
  ExternalSystemTaskId getExternalSystemTaskId();

  @Nullable
  String getIdeProjectPath();

  @NotNull
  String getProjectPath();

  @Nullable
  GradleExecutionSettings getSettings();

  @NotNull
  ProjectConnection getConnection();

  @NotNull
  CancellationToken getCancellationToken();

  @NotNull
  ExternalSystemTaskNotificationListener getListener();

  boolean isResolveModulePerSourceSet();

  boolean isUseQualifiedModuleNames();

  default boolean isDelegatedBuild() { return true; }

  @Nullable
  BuildEnvironment getBuildEnvironment();

  @NotNull
  Build getRootBuild();

  /**
   * Returns the list of the nested builds.
   * There are several types of nested builds:
   * <ul>
   * <li>Included builds</li>
   * <li>buildSrc builds for Gradle at least 8.0</li>
   * </ul>
   */
  @NotNull
  Collection<? extends Build> getNestedBuilds();

  @NotNull
  Collection<? extends Build> getAllBuilds();

  @Nullable
  <T> T getRootModel(@NotNull Class<T> modelClass);

  @Nullable
  <T> T getBuildModel(@NotNull BuildModel buildModel, @NotNull Class<T> modelClass);

  @Nullable
  <T> T getProjectModel(@NotNull ProjectModel projectModel, @NotNull Class<T> modelClass);

  /**
   * @deprecated use {@link #getRootModel} instead
   */
  @Deprecated
  default <T> @Nullable T getExtraProject(@NotNull Class<T> modelClass) {
    return getRootModel(modelClass);
  }

  @Nullable
  default <T> T getExtraProject(@Nullable IdeaModule module, @NotNull Class<T> modelClass) {
    return module == null ? getRootModel(modelClass) : getProjectModel(module, modelClass);
  }

  boolean hasModulesWithModel(@NotNull Class<?> modelClass);

  @Nullable
  String getProjectGradleVersion();

  @Nullable
  String getBuildSrcGroup();

  @Nullable
  String getBuildSrcGroup(IdeaModule module);

  @ApiStatus.Experimental
  void report(@NotNull MessageEvent.Kind kind, @NotNull BuildIssue buildIssue);

  @ApiStatus.Experimental
  @Nullable GradlePartialResolverPolicy getPolicy();

  /**
   * @return Maps of artifact paths to moduleIds
   */
  @NotNull
  ArtifactMappingService getArtifactsMap();
}
