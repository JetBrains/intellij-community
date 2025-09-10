// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.util.Pair;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

@ApiStatus.Internal
public final class DefaultGradleLightBuild implements GradleLightBuild, Serializable {

  private final @NotNull String myName;
  private final @NotNull DefaultBuildIdentifier myBuildIdentifier;
  private final @NotNull DefaultGradleLightProject myRootProject;
  private final @NotNull List<DefaultGradleLightProject> myProjects;

  private @Nullable DefaultGradleLightBuild myParentBuild = null;

  public DefaultGradleLightBuild(@NotNull GradleBuild gradleBuild, @NotNull GradleVersion gradleVersion) {
    BasicGradleProject rootGradleProject = gradleBuild.getRootProject();
    myName = rootGradleProject.getName();
    myBuildIdentifier = new DefaultBuildIdentifier(gradleBuild.getBuildIdentifier().getRootDir());

    Map<BasicGradleProject, DefaultGradleLightProject> projects = new LinkedHashMap<>();
    for (BasicGradleProject project : gradleBuild.getProjects()) {
      projects.put(project, new DefaultGradleLightProject(this, project, gradleVersion));
    }

    replicateModelHierarchy(
      rootGradleProject,
      it -> projects.get(it),
      BasicGradleProject::getChildren,
      DefaultGradleLightProject::addChildProject
    );

    myRootProject = projects.get(rootGradleProject);
    myProjects = new ArrayList<>(projects.values());
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull DefaultBuildIdentifier getBuildIdentifier() {
    return myBuildIdentifier;
  }

  @Override
  public @NotNull DefaultGradleLightProject getRootProject() {
    return myRootProject;
  }

  @Override
  public @NotNull List<DefaultGradleLightProject> getProjects() {
    return myProjects;
  }

  @Override
  public @Nullable DefaultGradleLightBuild getParentBuild() {
    return myParentBuild;
  }

  public void setParentBuild(@Nullable DefaultGradleLightBuild parentBuild) {
    myParentBuild = parentBuild;
  }

  @Override
  public String toString() {
    return "ProjectModel{" +
           "name='" + myName + '\'' +
           ", id=" + myBuildIdentifier +
           '}';
  }

  public static <ModelA, ModelB> void replicateModelHierarchy(
    @NotNull ModelA rootModelA,
    @NotNull Function<@NotNull ModelA, @Nullable ModelB> getModel,
    @NotNull Function<@NotNull ModelA, @NotNull Collection<? extends @NotNull ModelA>> getChildModel,
    @NotNull BiConsumer<@NotNull ModelB, @NotNull ModelB> addChildModel
  ) {
    ModelB rootModelB = getModel.apply(rootModelA);
    if (rootModelB == null) return;

    Queue<Pair<ModelA, ModelB>> queue = new ArrayDeque<>();
    queue.add(new Pair<>(rootModelA, rootModelB));

    while (!queue.isEmpty()) {

      Pair<ModelA, ModelB> parentModel = queue.remove();
      ModelA parentModelA = parentModel.getFirst();
      ModelB parentModelB = parentModel.getSecond();

      for (ModelA childModelA : getChildModel.apply(parentModelA)) {

        ModelB childModelB = getModel.apply(childModelA);
        if (childModelB == null) continue;

        queue.add(new Pair<>(childModelA, childModelB));

        addChildModel.accept(parentModelB, childModelB);
      }
    }
  }

  /**
   * @return {@code gradleBuilds} converted to {@link DefaultGradleLightBuild} instances.
   * Original order is preserved: if a root build is a first element of {@code gradleBuilds},
   * then the first element of the returned list is also a root build.
   */
  public static @NotNull List<DefaultGradleLightBuild> convertGradleBuilds(
    @NotNull Collection<? extends GradleBuild> gradleBuilds,
    @NotNull GradleVersion gradleVersion
  ) {
    Map<GradleBuild, DefaultGradleLightBuild> gradleBuildsToConverted = new LinkedHashMap<>();
    // TODO traverse builds via graph to avoid separated parent build field initialization
    for (GradleBuild gradleBuild : gradleBuilds) {
      DefaultGradleLightBuild build = new DefaultGradleLightBuild(gradleBuild, gradleVersion);
      gradleBuildsToConverted.put(gradleBuild, build);
    }
    setIncludedBuildsHierarchy(gradleBuilds, gradleBuildsToConverted);
    setBuildSrcHierarchy(gradleBuildsToConverted.values());
    return new ArrayList<>(gradleBuildsToConverted.values());
  }

  /// Sets parent builds for included builds, relying on the data provided by Gradle.
  private static void setIncludedBuildsHierarchy(
    @NotNull Collection<? extends GradleBuild> gradleBuilds,
    Map<GradleBuild, DefaultGradleLightBuild> gradleBuildsToConverted
  ) {
    for (GradleBuild gradleBuild : gradleBuilds) {
      DefaultGradleLightBuild build = gradleBuildsToConverted.get(gradleBuild);
      assert build != null;

      for (GradleBuild includedGradleBuild : gradleBuild.getIncludedBuilds()) {
        DefaultGradleLightBuild buildToUpdate = gradleBuildsToConverted.get(includedGradleBuild);
        assert buildToUpdate != null;
        buildToUpdate.setParentBuild(build);
      }
    }
  }

  /// Sets parent builds for buildSrc builds if any of `convertedBuilds` is located in a parent directory for buildSrc.
  private static void setBuildSrcHierarchy(@NotNull Collection<DefaultGradleLightBuild> convertedBuilds) {
    Map<Path, DefaultGradleLightBuild> pathToBuild = new HashMap<>();
    for (DefaultGradleLightBuild build : convertedBuilds) {
      pathToBuild.put(build.getBuildIdentifier().getRootDir().toPath(), build);
    }
    for (DefaultGradleLightBuild build : convertedBuilds) {
      if (!build.getName().equals("buildSrc")) continue;

      Path buildSrcPath = build.getBuildIdentifier().getRootDir().toPath();
      Path parentDirectory = buildSrcPath.getParent();
      if (parentDirectory == null) continue;

      DefaultGradleLightBuild parentBuild = pathToBuild.get(parentDirectory);
      if (parentBuild == null) continue;

      build.setParentBuild(parentBuild);
    }
  }
}
