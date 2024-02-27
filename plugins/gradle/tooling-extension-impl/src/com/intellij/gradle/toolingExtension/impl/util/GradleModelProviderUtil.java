// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import com.intellij.openapi.util.Pair;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GradleModelProviderUtil {

  public static <M> void buildModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    for (GradleBuild buildModel : buildModels) {
      buildModels(controller, buildModel, modelClass, consumer);
    }
  }

  public static <M> void buildModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
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
    @NotNull GradleModelConsumer consumer
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
    @NotNull GradleModelConsumer consumer
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
    traverseTree(buildModel.getRootProject(), BasicGradleProject::getChildren, (gradleProject) -> {
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
    backwardTraverseTree(buildModel.getRootProject(), BasicGradleProject::getChildren, (gradleProject) -> {
      M model = controller.findModel(gradleProject, modelClass);
      if (model != null) {
        consumer.consumeProjectModel(gradleProject, model, modelClass);
      }
    });
  }

  /**
   * Traverses a tree from root to leaf.
   */
  @VisibleForTesting
  public static <T> void traverseTree(
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

  /**
   * Traverses a tree from leaves to root.
   */
  @VisibleForTesting
  public static <T> void backwardTraverseTree(
    @NotNull T root,
    @NotNull Function<T, Collection<? extends T>> getChildren,
    @NotNull Consumer<T> action
  ) {
    T previous = root;

    Deque<T> stack = new ArrayDeque<>();
    stack.push(root);
    while (!stack.isEmpty()) {
      T current = stack.peek();
      List<? extends T> children = new ArrayList<>(getChildren.apply(current));
      if (children.isEmpty() || children.get(children.size() - 1) == previous) {
        current = stack.pop();
        action.accept(current);
        previous = current;
      }
      else {
        for (int i = children.size() - 1; i >= 0; i--) {
          stack.push(children.get(i));
        }
      }
    }
  }
}
