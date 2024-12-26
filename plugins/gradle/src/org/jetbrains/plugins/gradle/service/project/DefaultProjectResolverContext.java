// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.BuildIssueEventImpl;
import com.intellij.build.issue.BuildIssue;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.CollectionFactory;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleLightBuild;
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile;
import org.jetbrains.plugins.gradle.service.execution.GradleUserHomeUtil;
import org.jetbrains.plugins.gradle.service.modelAction.GradleIdeaModelHolder;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class DefaultProjectResolverContext extends UserDataHolderBase implements ProjectResolverContext {
  private final @NotNull ExternalSystemTaskId myExternalSystemTaskId;
  private final @NotNull String myProjectPath;
  private final @NotNull GradleExecutionSettings mySettings;
  private final @NotNull ExternalSystemTaskNotificationListener myListener;
  private final @NotNull GradleProjectResolverIndicator myProjectResolverIndicator;
  private @Nullable GradleIdeaModelHolder myModels;
  private File myGradleUserHome;
  private @Nullable String myProjectGradleVersion;
  private final boolean myBuildSrcProject;
  private @Nullable String myBuildSrcGroup;
  private @Nullable BuildEnvironment myBuildEnvironment;
  private final @Nullable GradlePartialResolverPolicy myPolicy;

  private final @NotNull ArtifactMappingService myArtifactsMap = new MapBasedArtifactMappingService(CollectionFactory.createFilePathMap());

  private @Nullable Boolean myPhasedSyncEnabled = null;
  private @Nullable Boolean myStreamingModelFetchingEnabled = null;

  private static final Logger LOG = Logger.getInstance(DefaultProjectResolverContext.class);

  public DefaultProjectResolverContext(
    @NotNull ExternalSystemTaskId externalSystemTaskId,
    @NotNull String projectPath,
    @NotNull GradleExecutionSettings settings,
    @NotNull ExternalSystemTaskNotificationListener listener,
    @Nullable GradlePartialResolverPolicy resolverPolicy,
    @NotNull GradleProjectResolverIndicator projectResolverIndicator,
    boolean isBuildSrcProject
  ) {
    myExternalSystemTaskId = externalSystemTaskId;
    myProjectPath = projectPath;
    mySettings = settings;
    myListener = listener;
    myPolicy = resolverPolicy;
    myProjectResolverIndicator = projectResolverIndicator;
    myBuildSrcProject = isBuildSrcProject;
  }

  public DefaultProjectResolverContext(
    @NotNull DefaultProjectResolverContext resolverContext,
    @NotNull String projectPath,
    @NotNull GradleExecutionSettings settings,
    boolean isBuildSrcProject
  ) {
    this(
      resolverContext.myExternalSystemTaskId,
      projectPath,
      settings,
      resolverContext.myListener,
      resolverContext.myPolicy,
      resolverContext.myProjectResolverIndicator,
      isBuildSrcProject
    );
    resolverContext.copyUserDataTo(this);
  }

  @Override
  public @NotNull ExternalSystemTaskId getExternalSystemTaskId() {
    return myExternalSystemTaskId;
  }

  @Override
  public @Nullable String getIdeProjectPath() {
    return mySettings.getIdeProjectPath();
  }

  @Override
  public @NotNull String getProjectPath() {
    return myProjectPath;
  }

  @Override
  public @NotNull GradleExecutionSettings getSettings() {
    return mySettings;
  }

  public @NotNull ProgressIndicator getProgressIndicator() {
    return myProjectResolverIndicator;
  }

  public @NotNull CancellationTokenSource getCancellationTokenSource() {
    return myProjectResolverIndicator;
  }

  @Override
  public @NotNull CancellationToken getCancellationToken() {
    return myProjectResolverIndicator.token();
  }

  public <R> R computeCancellable(@NotNull Supplier<R> action) {
    var result = new Ref<R>();
    runCancellable(() -> {
      result.set(action.get());
    });
    return result.get();
  }

  public void runCancellable(@NotNull Runnable action) {
    try {
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        ProgressManager.checkCanceled();
        action.run();
      }, myProjectResolverIndicator);
    }
    catch (ProcessCanceledException e) {
      myProjectResolverIndicator.cancel();
      throw e;
    }
  }

  @Override
  public @NotNull ExternalSystemTaskNotificationListener getListener() {
    return myListener;
  }

  @Override
  public boolean isPhasedSyncEnabled() {
    if (myPhasedSyncEnabled == null) {
      myPhasedSyncEnabled = isPhasedSyncEnabledImpl(this);
    }
    return myPhasedSyncEnabled;
  }

  private static boolean isPhasedSyncEnabledImpl(@NotNull ProjectResolverContext context) {
    if (!Registry.is("gradle.phased.sync.enabled")) {
      LOG.debug("The phased Gradle sync isn't applicable: disabled by registry");
      return false;
    }
    if (!context.isResolveModulePerSourceSet()) {
      LOG.debug("The phased Gradle sync isn't applicable: unsupported sync mode with isResolveModulePerSourceSet = false");
      return false;
    }
    if (!context.isUseQualifiedModuleNames()) {
      LOG.debug("The phased Gradle sync isn't applicable: unsupported sync mode with isUseQualifiedModuleNames = false");
      return false;
    }
    return true;
  }

  public boolean isStreamingModelFetchingEnabled() {
    if (myStreamingModelFetchingEnabled == null) {
      myStreamingModelFetchingEnabled = isStreamingModelFetchingEnabledImpl(this);
    }
    return myStreamingModelFetchingEnabled;
  }

  private static boolean isStreamingModelFetchingEnabledImpl(@NotNull ProjectResolverContext context) {
    if (!Registry.is("gradle.phased.sync.enabled")) {
      LOG.debug("The streaming Gradle model fetching isn't applicable: disabled by registry");
      return false;
    }
    var project = context.getExternalSystemTaskId().findProject();
    if (project == null) {
      String projectId = context.getExternalSystemTaskId().getIdeProjectId();
      LOG.debug("The streaming Gradle model fetching isn't applicable: project is closed: " + projectId);
      return false;
    }
    var gradleVersion = context.getProjectGradleVersion();
    if (gradleVersion == null) {
      LOG.debug("The streaming Gradle model fetching isn't applicable: Gradle version cannot be determined");
      return false;
    }
    if (GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.6")) {
      LOG.debug("The streaming Gradle model fetching isn't applicable: unsupported Gradle version: " + gradleVersion);
      return false;
    }
    if (GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.9")) {
      var projectPath = Path.of(context.getProjectPath());
      var properties = GradlePropertiesFile.getProperties(project, projectPath);
      var isolatedProjects = properties.getIsolatedProjects();
      if (isolatedProjects != null && isolatedProjects.getValue()) {
        LOG.debug("The streaming Gradle model fetching isn't applicable: unsupported isolated-projects mode: " + isolatedProjects);
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isResolveModulePerSourceSet() {
    return mySettings.isResolveModulePerSourceSet();
  }

  @Override
  public boolean isUseQualifiedModuleNames() {
    return mySettings.isUseQualifiedModuleNames();
  }

  @Override
  public boolean isDelegatedBuild() {
    return mySettings.isDelegatedBuild();
  }

  public File getGradleUserHome() {
    if (myGradleUserHome == null) {
      String serviceDirectory = mySettings.getServiceDirectory();
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
  public @NotNull GradleLightBuild getRootBuild() {
    return getModels().getRootBuild();
  }

  @Override
  public @NotNull Collection<? extends GradleLightBuild> getNestedBuilds() {
    return getModels().getNestedBuilds();
  }

  @Override
  public @NotNull Collection<? extends GradleLightBuild> getAllBuilds() {
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

  public boolean isBuildSrcProject() {
    return myBuildSrcProject;
  }

  public void setBuildSrcGroup(@Nullable String groupId) {
    myBuildSrcGroup = groupId;
  }

  @Override
  public @Nullable String getBuildSrcGroup() {
    return myBuildSrcGroup;
  }

  @Override
  public @Nullable String getBuildSrcGroup(@NotNull String rootName, @NotNull BuildIdentifier buildIdentifier) {
    if (!"buildSrc".equals(rootName)) {
      return myBuildSrcGroup;
    }
    String parentRootDir = buildIdentifier.getRootDir().getParent();
    return getAllBuilds().stream()
      .filter(b -> b.getBuildIdentifier().getRootDir().toString().equals(parentRootDir))
      .findFirst()
      .map(model -> model.getName())
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
