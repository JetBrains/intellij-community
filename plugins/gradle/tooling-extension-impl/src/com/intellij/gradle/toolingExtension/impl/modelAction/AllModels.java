// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.util.ArrayUtilRt;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.Build;
import org.jetbrains.plugins.gradle.model.DefaultBuild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Note: This class is NOT thread safe, and it is supposed to be used from a single thread.
 * Performance logging related methods are thread safe.
 */
public final class AllModels extends ModelsHolder<BuildModel, ProjectModel> {
  @NotNull private final List<Build> includedBuilds = new ArrayList<>();
  private transient Map<String, String> myBuildsKeyPrefixesMapping;
  private byte[] openTelemetryTrace = ArrayUtilRt.EMPTY_BYTE_ARRAY;

  public AllModels(@NotNull GradleBuild mainBuild) {
    super(DefaultBuild.convertGradleBuild(mainBuild));
  }

  public AllModels(@NotNull IdeaProject ideaProject) {
    super(DefaultBuild.convertIdeaProject(ideaProject));
    addModel(ideaProject, IdeaProject.class);
  }

  @NotNull
  public Build getMainBuild() {
    return (Build)getRootModel();
  }

  @NotNull
  public List<Build> getIncludedBuilds() {
    return includedBuilds;
  }

  @ApiStatus.Internal
  public void addIncludedBuild(@NotNull Build includedBuild) {
    includedBuilds.add(includedBuild);
  }

  @NotNull
  public List<Build> getAllBuilds() {
    List<Build> result = new ArrayList<>();
    result.add(getMainBuild());
    result.addAll(includedBuilds);
    return result;
  }

  @Nullable
  public BuildEnvironment getBuildEnvironment() {
    return getModel(BuildEnvironment.class);
  }

  public void setBuildEnvironment(@Nullable BuildEnvironment buildEnvironment) {
    if (buildEnvironment != null) {
      addModel(buildEnvironment, BuildEnvironment.class);
    }
  }

  public byte[] getOpenTelemetryTrace() {
    return openTelemetryTrace;
  }

  public void setOpenTelemetryTrace(byte[] openTelemetryTrace) {
    this.openTelemetryTrace = openTelemetryTrace;
  }

  @Override
  public void applyPathsConverter(@NotNull Consumer<Object> pathsConverter) {
    super.applyPathsConverter(pathsConverter);
    BuildEnvironment buildEnvironment = getBuildEnvironment();
    if (buildEnvironment != null) {
      pathsConverter.accept(buildEnvironment);
    }
    myBuildsKeyPrefixesMapping = new HashMap<>();
    convertPaths(pathsConverter, getMainBuild());
    for (Build includedBuild : includedBuilds) {
      convertPaths(pathsConverter, includedBuild);
    }
  }

  private void convertPaths(@NotNull Consumer<Object> fileMapper, @NotNull Build build) {
    String originalKey = getBuildKeyPrefix(build.getBuildIdentifier());
    fileMapper.accept(build);
    String currentKey = getBuildKeyPrefix(build.getBuildIdentifier());
    if (!originalKey.equals(currentKey)) {
      myBuildsKeyPrefixesMapping.put(currentKey, originalKey);
    }
  }

  @NotNull
  @Override
  protected String getBuildKeyPrefix(@NotNull BuildIdentifier buildIdentifier) {
    String currentKey = super.getBuildKeyPrefix(buildIdentifier);
    String originalKey = myBuildsKeyPrefixesMapping == null ? null : myBuildsKeyPrefixesMapping.get(currentKey);
    return originalKey == null ? currentKey : originalKey;
  }
}
