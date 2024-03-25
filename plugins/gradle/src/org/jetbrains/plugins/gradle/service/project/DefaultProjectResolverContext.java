// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.BuildIssueEventImpl;
import com.intellij.build.issue.BuildIssue;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.containers.CollectionFactory;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.Build;
import org.jetbrains.plugins.gradle.service.syncAction.GradleIdeaModelHolder;
import org.jetbrains.plugins.gradle.service.execution.GradleUserHomeUtil;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.io.File;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class DefaultProjectResolverContext extends UserDataHolderBase implements ProjectResolverContext {
  @NotNull private final ExternalSystemTaskId myExternalSystemTaskId;
  @NotNull private final String myProjectPath;
  @Nullable private final GradleExecutionSettings mySettings;
  @NotNull private final ExternalSystemTaskNotificationListener myListener;
  @NotNull private final CancellationTokenSource myCancellationTokenSource;
  private ProjectConnection myConnection;
  @Nullable private GradleIdeaModelHolder myModels;
  private File myGradleUserHome;
  @Nullable private String myProjectGradleVersion;
  @Nullable private String myBuildSrcGroup;
  @Nullable private BuildEnvironment myBuildEnvironment;
  @Nullable private final GradlePartialResolverPolicy myPolicy;

  @NotNull private final ArtifactMappingService myArtifactsMap = new MapBasedArtifactMappingService(CollectionFactory.createFilePathMap());

  public DefaultProjectResolverContext(
    @NotNull ExternalSystemTaskId externalSystemTaskId,
    @NotNull String projectPath,
    @Nullable GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @Nullable GradlePartialResolverPolicy resolverPolicy,
    @NotNull CancellationTokenSource cancellationTokenSource
  ) {
    myExternalSystemTaskId = externalSystemTaskId;
    myProjectPath = projectPath;
    mySettings = settings;
    myConnection = null;
    myListener = listener;
    myPolicy = resolverPolicy;
    myCancellationTokenSource = cancellationTokenSource;
  }

  public DefaultProjectResolverContext(
    @NotNull DefaultProjectResolverContext resolverContext,
    @NotNull String projectPath,
    @Nullable GradleExecutionSettings settings
  ) {
    this(
      resolverContext.myExternalSystemTaskId,
      projectPath,
      settings,
      resolverContext.myListener,
      resolverContext.myPolicy,
      resolverContext.myCancellationTokenSource
    );
    resolverContext.copyUserDataTo(this);
  }

  @NotNull
  @Override
  public ExternalSystemTaskId getExternalSystemTaskId() {
    return myExternalSystemTaskId;
  }

  @Nullable
  @Override
  public String getIdeProjectPath() {
    return mySettings != null ? mySettings.getIdeProjectPath() : null;
  }

  @NotNull
  @Override
  public String getProjectPath() {
    return myProjectPath;
  }

  @Nullable
  @Override
  public GradleExecutionSettings getSettings() {
    return mySettings;
  }

  @NotNull
  @Override
  public ProjectConnection getConnection() {
    return myConnection;
  }

  public void setConnection(@NotNull ProjectConnection connection) {
    myConnection = connection;
  }

  @NotNull
  @Override
  public CancellationTokenSource getCancellationTokenSource() {
    return myCancellationTokenSource;
  }

  @Override
  public boolean isCancellationRequested() {
    return myCancellationTokenSource.token().isCancellationRequested();
  }

  @Override
  public void cancel() {
    myCancellationTokenSource.cancel();
  }

  @Override
  public void checkCancelled() {
    if (isCancellationRequested()) {
      throw new ProcessCanceledException();
    }
  }

  @NotNull
  @Override
  public ExternalSystemTaskNotificationListener getListener() {
    return myListener;
  }

  @Override
  public boolean isResolveModulePerSourceSet() {
    return mySettings == null || mySettings.isResolveModulePerSourceSet();
  }

  @Override
  public boolean isUseQualifiedModuleNames() {
    return mySettings != null && mySettings.isUseQualifiedModuleNames();
  }

  @Override
  public boolean isDelegatedBuild() {
    return mySettings == null || mySettings.isDelegatedBuild();
  }

  public File getGradleUserHome() {
    if (myGradleUserHome == null) {
      String serviceDirectory = mySettings == null ? null : mySettings.getServiceDirectory();
      myGradleUserHome = serviceDirectory != null ? new File(serviceDirectory) : GradleUserHomeUtil.gradleUserHomeDir();
    }
    return myGradleUserHome;
  }

  public @NotNull GradleIdeaModelHolder getModels() {
    assert myModels != null;
    return myModels;
  }

  public void setModels(@NotNull GradleIdeaModelHolder models) {
    myModels = models;
  }

  @Override
  public @NotNull Build getRootBuild() {
    return getModels().getRootBuild();
  }

  @Override
  public @NotNull Collection<? extends Build> getNestedBuilds() {
    return getModels().getNestedBuilds();
  }

  @Override
  public @NotNull Collection<? extends Build> getAllBuilds() {
    return getModels().getAllBuilds();
  }

  @Override
  public <T> @Nullable T getRootModel(@NotNull Class<T> modelClass) {
    return getModels().getRootModel(modelClass);
  }

  @Override
  public <T> @Nullable T getBuildModel(@NotNull BuildModel buildModel, @NotNull Class<T> modelClass) {
    return getModels().getBuildModel(buildModel, modelClass);
  }

  @Override
  public <T> @Nullable T getProjectModel(@NotNull ProjectModel projectModel, @NotNull Class<T> modelClass) {
    return getModels().getProjectModel(projectModel, modelClass);
  }

  @Override
  public boolean hasModulesWithModel(@NotNull Class<?> modelClass) {
    return getModels().hasModulesWithModel(modelClass);
  }

  @Override
  public String getProjectGradleVersion() {
    if (myProjectGradleVersion == null) {
      var buildEnvironment = getBuildEnvironment();
      if (buildEnvironment != null) {
        myProjectGradleVersion = buildEnvironment.getGradle().getGradleVersion();
      }
    }
    return myProjectGradleVersion;
  }

  public void setBuildSrcGroup(@Nullable String groupId) {
    myBuildSrcGroup = groupId;
  }

  @Nullable
  @Override
  public String getBuildSrcGroup() {
    return myBuildSrcGroup;
  }

  @Nullable
  @Override
  public String getBuildSrcGroup(@NotNull IdeaModule module) {
    if (!"buildSrc".equals(module.getProject().getName())) {
      return myBuildSrcGroup;
    }
    String parentRootDir = module.getGradleProject().getProjectIdentifier().getBuildIdentifier().getRootDir().getParent();
    return getAllBuilds().stream()
      .filter(b -> b.getBuildIdentifier().getRootDir().toString().equals(parentRootDir))
      .findFirst()
      .map(Build::getName)
      .orElse(myBuildSrcGroup);
  }

  @Override
  public void report(@NotNull MessageEvent.Kind kind, @NotNull BuildIssue buildIssue) {
    BuildIssueEventImpl buildIssueEvent = new BuildIssueEventImpl(myExternalSystemTaskId, buildIssue, kind);
    myListener.onStatusChange(new ExternalSystemBuildEvent(myExternalSystemTaskId, buildIssueEvent));
  }

  void setBuildEnvironment(@NotNull BuildEnvironment buildEnvironment) {
    myBuildEnvironment = buildEnvironment;
  }

  @Override
  public @Nullable BuildEnvironment getBuildEnvironment() {
    if (myBuildEnvironment == null && myModels != null) {
      myBuildEnvironment = myModels.getBuildEnvironment();
    }
    return myBuildEnvironment;
  }

  @Override
  public @Nullable GradlePartialResolverPolicy getPolicy() {
    return myPolicy;
  }

  @Override
  public @NotNull ArtifactMappingService getArtifactsMap() {
    return myArtifactsMap;
  }
}
