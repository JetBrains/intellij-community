// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import io.opentelemetry.context.Context;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.*;
import com.intellij.gradle.toolingExtension.impl.modelSerialization.ModelConverter;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Part of {@link GradleModelFetchAction} which fully executes on Gradle side.
 */
@ApiStatus.Internal
public class GradleCustomModelFetchAction {

  private final @NotNull AllModels myAllModels;

  private final @NotNull Set<ProjectImportModelProvider> myModelProviders;
  private final @NotNull ModelConverter myModelConverter;
  private final @NotNull ExecutorService myModelConverterExecutor;

  private final @NotNull GradleOpenTelemetry myTelemetry;

  public GradleCustomModelFetchAction(
    @NotNull AllModels allModels,
    @NotNull Set<ProjectImportModelProvider> modelProviders,
    @NotNull ModelConverter modelConverter,
    @NotNull ExecutorService modelConverterExecutor,
    @NotNull GradleOpenTelemetry telemetry
  ) {
    myAllModels = allModels;

    myModelProviders = modelProviders;
    myModelConverter = modelConverter;
    myModelConverterExecutor = modelConverterExecutor;

    myTelemetry = telemetry;
  }

  private void forEachModelFetchPhase(@NotNull BiConsumer<GradleModelFetchPhase, List<ProjectImportModelProvider>> consumer) {
    myModelProviders.stream()
      .collect(Collectors.groupingBy(it -> it.getPhase())).entrySet().stream()
      .sorted(Map.Entry.comparingByKey())
      .forEachOrdered(it -> consumer.accept(it.getKey(), it.getValue()));
  }

  public void execute(
    @NotNull BuildController controller,
    @NotNull GradleBuild rootGradleBuild,
    @NotNull Collection<? extends GradleBuild> nestedGradleBuilds
  ) {
    try {
      forEachModelFetchPhase((phase, modelProviders) -> {
        myTelemetry.runWithSpan(phase.name(), span -> {
          addModels(controller, rootGradleBuild, modelProviders);
          for (GradleBuild nestedGradleBuild : nestedGradleBuilds) {
            addModels(controller, nestedGradleBuild, modelProviders);
          }
        });
      });
    }
    catch (Exception e) {
      throw new ExternalSystemException(e);
    }
  }

  private void addModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild gradleBuild,
    @NotNull Collection<ProjectImportModelProvider> modelProviders
  ) {
    myTelemetry.runWithSpan("AddProjectModels", span -> {
      for (BasicGradleProject gradleProject : gradleBuild.getProjects()) {
        for (ProjectImportModelProvider modelProvider : modelProviders) {
          addProjectModels(controller, gradleProject, modelProvider);
        }
      }
    });
    myTelemetry.runWithSpan("AddBuildModels", span -> {
      for (ProjectImportModelProvider modelProvider : modelProviders) {
        addBuildModels(controller, gradleBuild, modelProvider);
      }
    });
  }

  private void addProjectModels(
    @NotNull BuildController controller,
    @NotNull BasicGradleProject gradleProject,
    @NotNull ProjectImportModelProvider modelProvider
  ) {
    myTelemetry.runWithSpan(modelProvider.getName(), span -> {
      modelProvider.populateProjectModels(controller, gradleProject, new ProjectImportModelProvider.GradleModelConsumer() {
        @Override
        public void consumeProjectModel(@NotNull BasicGradleProject projectModel, @NotNull Object object, @NotNull Class<?> clazz) {
          addProjectModel(gradleProject, object, clazz);
        }
      });
    });
  }

  private void addBuildModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild gradleBuild,
    @NotNull ProjectImportModelProvider modelProvider
  ) {
    myTelemetry.runWithSpan(modelProvider.getName(), span -> {
      modelProvider.populateBuildModels(controller, gradleBuild, new ProjectImportModelProvider.GradleModelConsumer() {
        @Override
        public void consumeProjectModel(@NotNull BasicGradleProject projectModel, @NotNull Object object, @NotNull Class<?> clazz) {
          addProjectModel(projectModel, object, clazz);
        }

        @Override
        public void consumeBuildModel(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class<?> clazz) {
          addBuildModel(buildModel, object, clazz);
        }
      });
    });
  }

  private void addProjectModel(@NotNull ProjectModel projectModel,
                               @NotNull Object object,
                               @NotNull Class<?> clazz) {
    convertModel(object, clazz, "ProjectModelConverter", converted -> myAllModels.addModel(converted, clazz, projectModel));
  }

  private void addBuildModel(@NotNull BuildModel buildModel,
                             @NotNull Object object,
                             @NotNull Class<?> clazz) {
    convertModel(object, clazz, "BuildModelConverter", converted -> myAllModels.addModel(converted, clazz, buildModel));
  }

  private void convertModel(@NotNull Object object,
                            @NotNull Class<?> clazz,
                            @NotNull String spanName,
                            @NotNull Consumer<Object> onConvertorEnd) {
    Context.current()
      .wrap(myModelConverterExecutor)
      .execute(() -> {
        Object converted = myTelemetry.callWithSpan(spanName, span -> {
          span.setAttribute("model.class", clazz.getName());
          return myModelConverter.convert(object);
        });
        onConvertorEnd.accept(converted);
      });
  }
}
