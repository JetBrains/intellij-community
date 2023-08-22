// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelProvider;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.openapi.util.Pair;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

import java.util.*;

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
    Map<BasicGradleProject, DefaultExternalProject> externalProjectMap = collectExternalProjects(controller, buildModel);
    DefaultExternalProject rootExternalProject = buildExternalProjectHierarchy(buildModel, externalProjectMap);
    if (rootExternalProject != null) {
      consumer.consume(buildModel, rootExternalProject, ExternalProject.class);
    }
  }

  private static Map<BasicGradleProject, DefaultExternalProject> collectExternalProjects(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel
  ) {
    Map<BasicGradleProject, DefaultExternalProject> result = new HashMap<>();
    for (BasicGradleProject gradleProject : buildModel.getProjects()) {
      ExternalProject model = controller.findModel(gradleProject, ExternalProject.class);
      if (model != null) {
        result.put(gradleProject, new DefaultExternalProject(model));
      }
    }
    return result;
  }

  private static @Nullable DefaultExternalProject buildExternalProjectHierarchy(
    @NotNull GradleBuild buildModel,
    @NotNull Map<BasicGradleProject, DefaultExternalProject> externalProjectMap
  ) {
    Queue<Pair<BasicGradleProject, DefaultExternalProject>> queue = new ArrayDeque<>();
    BasicGradleProject rootGradleProject = buildModel.getRootProject();
    DefaultExternalProject rootExternalProject = externalProjectMap.get(rootGradleProject);
    if (rootExternalProject != null) {
      queue.add(new Pair<>(rootGradleProject, rootExternalProject));
    }
    while (!queue.isEmpty()) {
      Pair<BasicGradleProject, DefaultExternalProject> parentProject = queue.remove();
      BasicGradleProject parentGradleProject = parentProject.getFirst();
      DefaultExternalProject parentExternalProject = parentProject.getSecond();
      Map<String, DefaultExternalProject> childExternalProjects = new HashMap<>();
      for (BasicGradleProject childGradleProject : parentGradleProject.getChildren()) {
        DefaultExternalProject childExternalProject = externalProjectMap.get(childGradleProject);
        if (childExternalProject != null) {
          childExternalProjects.put(childGradleProject.getName(), childExternalProject);
          queue.add(new Pair<>(childGradleProject, childExternalProject));
        }
      }
      parentExternalProject.setChildProjects(childExternalProjects);
    }
    return rootExternalProject;
  }
}
