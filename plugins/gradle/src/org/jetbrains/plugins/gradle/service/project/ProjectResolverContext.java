// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.util.UserDataHolderEx;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleLightBuild;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext;

import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.NonExtendable
public interface ProjectResolverContext extends GradleExecutionContext, UserDataHolderEx {

  @NotNull ExternalSystemTaskId getExternalSystemTaskId();

  @NotNull String getProjectGradleVersion();

  @Nullable String getIdeProjectPath();

  @ApiStatus.Internal
  boolean isPhasedSyncEnabled();

  boolean isResolveModulePerSourceSet();

  boolean isUseQualifiedModuleNames();

  boolean isDelegatedBuild();

  @NotNull
  GradleLightBuild getRootBuild();

  /**
   * Returns the list of the nested builds.
   * There are several types of nested builds:
   * <ul>
   * <li>Included builds</li>
   * <li>buildSrc builds for Gradle at least 8.0</li>
   * </ul>
   */
  @NotNull
  Collection<? extends GradleLightBuild> getNestedBuilds();

  @NotNull
  Collection<? extends GradleLightBuild> getAllBuilds();

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

  default @Nullable <T> T getExtraProject(@Nullable IdeaModule module, @NotNull Class<T> modelClass) {
    return module == null ? getRootModel(modelClass) : getProjectModel(module, modelClass);
  }

  boolean hasModulesWithModel(@NotNull Class<?> modelClass);

  @Nullable
  String getBuildSrcGroup();

  @Nullable
  String getBuildSrcGroup(@NotNull String rootName, @NotNull BuildIdentifier buildIdentifier);

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
