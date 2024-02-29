// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.Build;
import org.jetbrains.plugins.gradle.model.DefaultBuild;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildEnvironment;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * Note: This class is NOT thread safe, and it is supposed to be used from a single thread.
 * Performance logging related methods are thread safe.
 */
@ApiStatus.Internal
public final class AllModels extends ModelsHolder<BuildModel, ProjectModel> {
  @NotNull private final List<Build> includedBuilds = new ArrayList<>();
  private transient Map<String, String> myBuildsKeyPrefixesMapping;

  private final List<byte[]> myOpenTelemetryTraces = new ArrayList<>();

  public AllModels(
    @NotNull GradleBuild mainGradleBuild,
    @NotNull Collection<? extends GradleBuild> nestedGradleBuilds,
    @NotNull BuildEnvironment buildEnvironment
  ) {
    super(DefaultBuild.convertGradleBuild(mainGradleBuild));

    for (GradleBuild includedBuild : nestedGradleBuilds) {
      includedBuilds.add(DefaultBuild.convertGradleBuild(includedBuild));
    }
    setupIncludedBuildsHierarchy(includedBuilds, nestedGradleBuilds);

    setBuildEnvironment(InternalBuildEnvironment.convertBuildEnvironment(buildEnvironment));
  }

  @NotNull
  public Build getMainBuild() {
    return (Build)getRootModel();
  }

  @NotNull
  public List<Build> getIncludedBuilds() {
    return includedBuilds;
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

  public List<byte[]> getOpenTelemetryTraces() {
    return myOpenTelemetryTraces;
  }

  public void addOpenTelemetryTrace(byte[] openTelemetryTrace) {
    myOpenTelemetryTraces.add(openTelemetryTrace);
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

  private static void setupIncludedBuildsHierarchy(
    @NotNull Collection<? extends Build> builds,
    @NotNull Collection<? extends GradleBuild> gradleBuilds
  ) {
    Set<Build> updatedBuilds = new HashSet<>();
    Map<File, Build> rootDirsToBuilds = new HashMap<>();
    for (Build build : builds) {
      rootDirsToBuilds.put(build.getBuildIdentifier().getRootDir(), build);
    }

    for (GradleBuild gradleBuild : gradleBuilds) {
      Build build = rootDirsToBuilds.get(gradleBuild.getBuildIdentifier().getRootDir());
      if (build == null) {
        continue;
      }

      for (GradleBuild includedGradleBuild : gradleBuild.getIncludedBuilds()) {
        Build buildToUpdate = rootDirsToBuilds.get(includedGradleBuild.getBuildIdentifier().getRootDir());
        if (buildToUpdate instanceof DefaultBuild && updatedBuilds.add(buildToUpdate)) {
          ((DefaultBuild)buildToUpdate).setParentBuildIdentifier(
            new DefaultBuildIdentifier(gradleBuild.getBuildIdentifier().getRootDir()));
        }
      }
    }
  }
}
