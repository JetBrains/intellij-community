// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import com.intellij.openapi.util.Pair;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer;

import java.util.*;

public final class GradleModelProviderUtil {

  public static <M> void buildModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    buildModels(controller, Collections.singleton(buildModel), modelClass, consumer);
  }

  public static <M> void buildModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    if (Objects.equals(System.getProperty("idea.parallelModelFetch.enabled"), "true")) {
      buildModelsInParallel(controller, buildModels, modelClass, consumer);
    }
    else {
      buildModelsInSequence(controller, buildModels, modelClass, consumer);
    }
  }

  public static <M> void buildModelsInParallel(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    List<BuildAction<Pair<BasicGradleProject, M>>> buildActions = new ArrayList<>();
    for (GradleBuild buildModel : buildModels) {
      for (BasicGradleProject gradleProject : buildModel.getProjects()) {
        buildActions.add((BuildAction<Pair<BasicGradleProject, M>>)innerController -> {
          M model = innerController.findModel(gradleProject, modelClass);
          if (model == null) {
            return null;
          }
          return new Pair<>(gradleProject, model);
        });
      }
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
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    for (GradleBuild buildModel : buildModels) {
      for (BasicGradleProject gradleProject : buildModel.getProjects()) {
        M model = controller.findModel(gradleProject, modelClass);
        if (model != null) {
          consumer.consumeProjectModel(gradleProject, model, modelClass);
        }
      }
    }
  }

  public static <M> void buildModelsRecursively(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    for (GradleBuild buildModel : buildModels) {
      buildModelsRecursively(controller, buildModel, modelClass, consumer);
    }
  }

  public static <M> void buildModelsRecursively(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    @NotNull BasicGradleProject root = buildModel.getRootProject();
    GradleTreeTraverserUtil.traverseTree(root, BasicGradleProject::getChildren, (gradleProject) -> {
      M model = controller.findModel(gradleProject, modelClass);
      if (model != null) {
        consumer.consumeProjectModel(gradleProject, model, modelClass);
      }
    });
  }

  public static <M> void buildModelsBackwardRecursively(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    @NotNull BasicGradleProject root = buildModel.getRootProject();
    GradleTreeTraverserUtil.backwardTraverseTree(root, BasicGradleProject::getChildren, (gradleProject) -> {
      M model = controller.findModel(gradleProject, modelClass);
      if (model != null) {
        consumer.consumeProjectModel(gradleProject, model, modelClass);
      }
    });
  }
}
