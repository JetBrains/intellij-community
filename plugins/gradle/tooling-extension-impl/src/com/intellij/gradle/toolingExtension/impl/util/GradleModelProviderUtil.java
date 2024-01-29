// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import com.intellij.openapi.util.Pair;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.BuildModelConsumer;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GradleModelProviderUtil {

  private static <T, M> @NotNull List<Pair<T, M>> buildModelsInParallel(
    @NotNull BuildController controller,
    @NotNull Iterable<T> targets,
    @NotNull BiFunction<? super BuildController, ? super T, ? extends M> buildAction
  ) {
    List<BuildAction<Pair<T, M>>> buildActions = new ArrayList<>();
    for (T target : targets) {
      buildActions.add((BuildAction<Pair<T, M>>)innerController -> {
        M model = buildAction.apply(innerController, target);
        if (model == null) {
          return null;
        }
        return new Pair<>(target, model);
      });
    }

    return controller.run(buildActions);
  }

  private static <T, M> @NotNull List<Pair<T, M>> buildModelsSequentially(
    @NotNull BuildController controller,
    @NotNull Iterable<T> targets,
    @NotNull BiFunction<? super BuildController, ? super T, ? extends M> buildAction
  ) {
    List<Pair<T, M>> result = new ArrayList<>();
    for (T target : targets) {
      M model = buildAction.apply(controller, target);
      if (model != null) {
        result.add(new Pair<>(target, model));
      }
    }
    return result;
  }

  public static <T, M> @NotNull List<Pair<T, M>> buildModels(
    @NotNull BuildController controller,
    @NotNull Iterable<T> targets,
    @NotNull BiFunction<? super BuildController, ? super T, ? extends M> buildAction
  ) {
    if (Objects.equals(System.getProperty("idea.parallelModelFetch.enabled"), "true")) {
      return buildModelsInParallel(controller, targets, buildAction);
    }
    else {
      return buildModelsSequentially(controller, targets, buildAction);
    }
  }

  public static <T extends Model, M> @NotNull List<Pair<T, M>> buildModels(
    @NotNull BuildController controller,
    @NotNull Iterable<T> targets,
    @NotNull Class<M> modelClass
  ) {
    return buildModels(controller, targets, (innerController, target) ->
      innerController.findModel(target, modelClass)
    );
  }

  public static <T extends Model, M> void buildModels(
    @NotNull BuildController controller,
    @NotNull Iterable<T> targets,
    @NotNull Class<M> modelClass,
    @NotNull BiConsumer<T, M> modelConsumer
  ) {
    List<Pair<T, M>> models = buildModels(controller, targets, modelClass);
    for (Pair<T, M> model : models) {
      modelConsumer.accept(model.first, model.second);
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
