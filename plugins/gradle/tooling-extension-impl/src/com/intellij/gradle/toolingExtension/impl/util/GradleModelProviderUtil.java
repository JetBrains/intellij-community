// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import com.intellij.openapi.util.Pair;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.BuildModelConsumer;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GradleModelProviderUtil {

  public static <M> void buildModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull Class<M> modelClass,
    @NotNull BuildModelConsumer consumer
  ) {
    if (Objects.equals(System.getProperty("idea.parallelModelFetch.enabled"), "true")) {
      buildModelsInParallel(controller, buildModel, modelClass, consumer);
    }
    else {
      buildModelsInSequence(controller, buildModel, modelClass, consumer);
    }
  }

  private static <M> void buildModelsInParallel(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull Class<M> modelClass,
    @NotNull BuildModelConsumer consumer
  ) {
    List<BuildAction<Pair<BasicGradleProject, M>>> buildActions = new ArrayList<>();
    for (BasicGradleProject gradleProject : buildModel.getProjects()) {
      buildActions.add((BuildAction<Pair<BasicGradleProject, M>>)innerController -> {
        M model = innerController.findModel(gradleProject, modelClass);
        if (model == null) {
          return null;
        }
        return new Pair<>(gradleProject, model);
      });
    }
    List<Pair<BasicGradleProject, M>> models = controller.run(buildActions);
    for (Pair<BasicGradleProject, M> model : models) {
      if (model != null) {
        consumer.consumeProjectModel(model.first, model.second, modelClass);
      }
    }
  }

  private static <M> void buildModelsInSequence(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull Class<M> modelClass,
    @NotNull BuildModelConsumer consumer
  ) {
    for (BasicGradleProject gradleProject : buildModel.getProjects()) {
      M model = controller.findModel(gradleProject, modelClass);
      if (model != null) {
        consumer.consumeProjectModel(gradleProject, model, modelClass);
      }
    }
  }

  public static <M> void buildModelsRecursively(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull Class<M> modelClass,
    @NotNull BuildModelConsumer consumer
  ) {
    traverseTree(buildModel.getRootProject(), BasicGradleProject::getChildren, (gradleProject) -> {
      M model = controller.findModel(gradleProject, modelClass);
      if (model != null) {
        consumer.consumeProjectModel(gradleProject, model, modelClass);
      }
    });
  }

  private static <T> void traverseTree(
    @NotNull T root,
    @NotNull Function<T, Iterable<? extends T>> children,
    @NotNull Consumer<T> action
  ) {
    Queue<T> queue = new ArrayDeque<>();
    action.accept(root);
    queue.add(root);
    while (!queue.isEmpty()) {
      T parent = queue.remove();
      for (T child : children.apply(parent)) {
        action.accept(child);
        queue.add(child);
      }
    }
  }
}
