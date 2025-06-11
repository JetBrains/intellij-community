// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.util.Pair;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class DefaultGradleLightBuild implements GradleLightBuild, Serializable {

  private final @NotNull String myName;
  private final @NotNull DefaultBuildIdentifier myBuildIdentifier;
  private final @NotNull DefaultGradleLightProject myRootProject;
  private final @NotNull List<DefaultGradleLightProject> myProjects;

  private @Nullable DefaultBuildIdentifier myParentBuildIdentifier = null;

  public DefaultGradleLightBuild(@NotNull GradleBuild gradleBuild) {
    BasicGradleProject rootGradleProject = gradleBuild.getRootProject();
    myName = rootGradleProject.getName();
    myBuildIdentifier = new DefaultBuildIdentifier(gradleBuild.getBuildIdentifier().getRootDir());

    Map<BasicGradleProject, DefaultGradleLightProject> projects = gradleBuild.getProjects().stream()
      .map(it -> new Pair<>(it, new DefaultGradleLightProject(this, it)))
      .collect(Collectors.toMap(it -> it.getFirst(), it -> it.getSecond()));

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
  public @Nullable DefaultBuildIdentifier getParentBuildIdentifier() {
    return myParentBuildIdentifier;
  }

  public void setParentBuildIdentifier(@Nullable DefaultBuildIdentifier parentBuildIdentifier) {
    myParentBuildIdentifier = parentBuildIdentifier;
  }

  @Override
  public String toString() {
    return "ProjectModel{" +
           "name='" + myName + '\'' +
           ", id=" + myBuildIdentifier +
           '}';
  }

  public static @NotNull DefaultGradleLightBuild convertGradleBuild(@NotNull GradleBuild gradleBuild) {
    return new DefaultGradleLightBuild(gradleBuild);
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
}
