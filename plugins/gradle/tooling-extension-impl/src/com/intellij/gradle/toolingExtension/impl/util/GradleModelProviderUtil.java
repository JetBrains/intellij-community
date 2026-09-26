// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelControllerImpl;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleExecutionMode;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleTraversalMode;
import org.gradle.api.Action;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer;

import java.util.Collection;

/**
 * @deprecated use {@link GradleModelController} instead.
 */
@Deprecated
@SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
public final class GradleModelProviderUtil {

  public static <M> void buildModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    new GradleModelControllerImpl(controller)
      .fetchRequest(buildModels, modelClass)
      .execute(consumer);
  }

  public static <M> void buildModelsInParallel(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    new GradleModelControllerImpl(controller)
      .fetchRequest(buildModels, modelClass)
      .executionMode(GradleExecutionMode.PARALLEL)
      .execute(consumer);
  }

  public static <M> void buildModelsInSequence(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    new GradleModelControllerImpl(controller)
      .fetchRequest(buildModels, modelClass)
      .executionMode(GradleExecutionMode.SEQUENTIAL)
      .execute(consumer);
  }

  public static <M> void buildModelsRecursively(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer
  ) {
    new GradleModelControllerImpl(controller)
      .fetchRequest(buildModels, modelClass)
      .executionMode(GradleExecutionMode.SEQUENTIAL)
      .traversalMode(GradleTraversalMode.RECURSIVE)
      .execute(consumer);
  }

  public static <M, P> void buildModelsWithParameter(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull Class<M> modelClass,
    @NotNull GradleModelConsumer consumer,
    @NotNull Class<P> parameterClass,
    @NotNull Action<? super P> action
  ) {
    new GradleModelControllerImpl(controller)
      .fetchRequest(buildModels, modelClass)
      .parameter(parameterClass, action)
      .execute(consumer);
  }
}
