// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.projectModel;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.openapi.util.Pair;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

@ApiStatus.Internal
public class GradleExternalProjectModelProvider implements ProjectImportModelProvider {

  private static final long serialVersionUID = 1L;

  @Override
  public GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.PROJECT_MODEL_PHASE;
  }

  @Override
  public void populateBuildModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull BuildModelConsumer consumer
  ) {
    Map<BasicGradleProject, PartialExternalProject> externalProjectMap = collectExternalProjects(controller, buildModel);
    PartialExternalProject rootExternalProject = buildExternalProjectHierarchy(buildModel, externalProjectMap);
    if (rootExternalProject != null) {
      consumer.consume(buildModel, rootExternalProject, ExternalProject.class);
    }
  }

  private static Map<BasicGradleProject, PartialExternalProject> collectExternalProjects(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel
  ) {
    Map<BasicGradleProject, PartialExternalProject> result = new HashMap<>();
    for (BasicGradleProject gradleProject : buildModel.getProjects()) {
      ExternalProject model = controller.findModel(gradleProject, ExternalProject.class);
      if (model != null) {
        result.put(gradleProject, new PartialExternalProject(model));
      }
    }
    return result;
  }

  private static @Nullable PartialExternalProject buildExternalProjectHierarchy(
    @NotNull GradleBuild buildModel,
    @NotNull Map<BasicGradleProject, PartialExternalProject> externalProjectMap
  ) {
    Queue<Pair<BasicGradleProject, PartialExternalProject>> queue = new ArrayDeque<>();
    BasicGradleProject rootGradleProject = buildModel.getRootProject();
    PartialExternalProject rootExternalProject = externalProjectMap.get(rootGradleProject);
    if (rootExternalProject != null) {
      queue.add(new Pair<>(rootGradleProject, rootExternalProject));
    }
    while (!queue.isEmpty()) {
      Pair<BasicGradleProject, PartialExternalProject> parentProject = queue.remove();
      BasicGradleProject parentGradleProject = parentProject.getFirst();
      PartialExternalProject parentExternalProject = parentProject.getSecond();
      Map<String, PartialExternalProject> childExternalProjects = new HashMap<>();
      for (BasicGradleProject childGradleProject : parentGradleProject.getChildren()) {
        PartialExternalProject childExternalProject = externalProjectMap.get(childGradleProject);
        if (childExternalProject != null) {
          childExternalProjects.put(childGradleProject.getName(), childExternalProject);
          queue.add(new Pair<>(childGradleProject, childExternalProject));
        }
      }
      parentExternalProject.setChildProjects(childExternalProjects);
    }
    return rootExternalProject;
  }

  /**
   * This class allows to modify {@link ExternalProject} without resolving {@link java.lang.reflect.Proxy} references,
   * and without coping all collected data in a delegated project,
   * unlike {@link org.jetbrains.plugins.gradle.model.DefaultExternalProject}
   */
  private static final class PartialExternalProject extends DelegateExternalProject {

    private @NotNull Map<String, PartialExternalProject> childProjects = new HashMap<>();

    private PartialExternalProject(@NotNull ExternalProject externalProject) {
      super(externalProject);
    }

    @Override
    public @NotNull Map<String, PartialExternalProject> getChildProjects() {
      return childProjects;
    }

    public void setChildProjects(@NotNull Map<String, PartialExternalProject> childProjects) {
      this.childProjects = childProjects;
    }
  }
}
