// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Part of {@link GradleModelFetchAction} which fully executes on Gradle side.
 */
@ApiStatus.Internal
public class GradleCustomModelFetchAction {

  private final @NotNull GradleOpenTelemetry myTelemetry;

  private final @NotNull GradleModelConsumer myModelConsumer;
  private final @NotNull Set<ProjectImportModelProvider> myModelProviders;


  public GradleCustomModelFetchAction(
    @NotNull GradleOpenTelemetry telemetry,
    @NotNull GradleModelConsumer modelConsumer,
    @NotNull Set<ProjectImportModelProvider> modelProviders
  ) {
    myTelemetry = telemetry;
    myModelConsumer = modelConsumer;
    myModelProviders = modelProviders;
  }

  private void forEachModelFetchPhase(@NotNull BiConsumer<GradleModelFetchPhase, List<ProjectImportModelProvider>> consumer) {
    myModelProviders.stream()
      .collect(Collectors.groupingBy(it -> it.getPhase())).entrySet().stream()
      .sorted(Map.Entry.comparingByKey())
      .forEachOrdered(it -> consumer.accept(it.getKey(), it.getValue()));
  }

  public void execute(@NotNull BuildController controller, @NotNull Collection<? extends GradleBuild> gradleBuilds) {
    try {
      forEachModelFetchPhase((phase, modelProviders) -> {
        myTelemetry.runWithSpan(phase.name(), __ -> {
          for (GradleBuild gradleBuild : gradleBuilds) {
            for (BasicGradleProject gradleProject : gradleBuild.getProjects()) {
              for (ProjectImportModelProvider modelProvider : modelProviders) {
                myTelemetry.runWithSpan(modelProvider.getName(), span -> {
                  span.setAttribute("build-name", gradleBuild.getBuildIdentifier().getRootDir().getName());
                  span.setAttribute("model-type", "ProjectModel");
                  modelProvider.populateProjectModels(controller, gradleProject, myModelConsumer);
                });
              }
            }
            for (ProjectImportModelProvider modelProvider : modelProviders) {
              myTelemetry.runWithSpan(modelProvider.getName(), span -> {
                span.setAttribute("build-name", gradleBuild.getBuildIdentifier().getRootDir().getName());
                span.setAttribute("model-type", "BuildModel");
                modelProvider.populateBuildModels(controller, gradleBuild, myModelConsumer);
              });
            }
          }
        });
      });
    }
    catch (Exception e) {
      throw new ExternalSystemException(e);
    }
  }
}
