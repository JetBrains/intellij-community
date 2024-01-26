// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import io.opentelemetry.context.Context;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.internal.TurnOffDefaultTasks;
import org.jetbrains.plugins.gradle.tooling.serialization.ModelConverter;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Part of {@link ProjectImportAction} which fully executes on Gradle side.
 */
@ApiStatus.Internal
public class GradleModelFetchAction {

  private final @NotNull ProjectImportAction.AllModels myAllModels;

  private final @NotNull Set<ProjectImportModelProvider> myModelProviders;
  private final @NotNull ModelConverter myModelConverter;
  private final @NotNull ExecutorService myModelConverterExecutor;
  private final @NotNull GradleOpenTelemetry myTelemetry;

  private final boolean myIsPreviewMode;
  private final boolean myIsProjectsLoadedAction;

  public GradleModelFetchAction(
    @NotNull ProjectImportAction.AllModels allModels,
    @NotNull Set<ProjectImportModelProvider> modelProviders,
    @NotNull ModelConverter modelConverter,
    @NotNull ExecutorService modelConverterExecutor,
    @NotNull GradleOpenTelemetry telemetry,
    boolean isPreviewMode,
    boolean isProjectsLoadedAction
  ) {
    myAllModels = allModels;

    myModelProviders = modelProviders;
    myModelConverter = modelConverter;
    myModelConverterExecutor = modelConverterExecutor;
    myTelemetry = telemetry;

    myIsPreviewMode = isPreviewMode;
    myIsProjectsLoadedAction = isProjectsLoadedAction;
  }

  public void execute(@NotNull DefaultBuildController controller) {
    GradleBuild mainGradleBuild = controller.getBuildModel();

    //We only need these later, but need to fetch them before fetching other models because of https://github.com/gradle/gradle/issues/20008
    Set<GradleBuild> nestedBuilds = myTelemetry.callWithSpan("NestedBuildsResolve", span -> getNestedBuilds(controller, mainGradleBuild));

    myTelemetry.runWithSpan("MainBuildAddModels", span -> addModels(controller, mainGradleBuild));
    myTelemetry.runWithSpan("IncludedBuildAddModels", span -> {
      for (GradleBuild includedBuild : nestedBuilds) {
        if (!myIsProjectsLoadedAction) {
          myAllModels.addIncludedBuild(DefaultBuild.convertGradleBuild(includedBuild));
        }
        addModels(controller, includedBuild);
      }
    });
    setupIncludedBuildsHierarchy(myAllModels.getIncludedBuilds(), nestedBuilds);
    if (myIsProjectsLoadedAction) {
      controller.getModel(TurnOffDefaultTasks.class);
    }
  }

  private static Set<GradleBuild> getNestedBuilds(@NotNull BuildController controller, @NotNull GradleBuild build) {
    BuildEnvironment environment = controller.getModel(BuildEnvironment.class);
    if (environment == null) {
      return Collections.emptySet();
    }
    GradleVersion gradleVersion = GradleVersion.version(environment.getGradle().getGradleVersion());
    Set<String> processedBuildsPaths = new HashSet<>();
    Set<GradleBuild> nestedBuilds = new LinkedHashSet<>();
    String rootBuildPath = build.getBuildIdentifier().getRootDir().getPath();
    processedBuildsPaths.add(rootBuildPath);
    Queue<GradleBuild> queue = new ArrayDeque<>(getEditableBuilds(build, gradleVersion));
    while (!queue.isEmpty()) {
      GradleBuild includedBuild = queue.remove();
      String includedBuildPath = includedBuild.getBuildIdentifier().getRootDir().getPath();
      if (processedBuildsPaths.add(includedBuildPath)) {
        nestedBuilds.add(includedBuild);
        queue.addAll(getEditableBuilds(includedBuild, gradleVersion));
      }
    }
    return nestedBuilds;
  }

  /**
   * Get nested builds to be imported by IDEA
   *
   * @param build parent build
   * @return builds to be imported by IDEA. Before Gradle 8.0 - included builds, 8.0 and later - included and buildSrc builds
   */
  private static DomainObjectSet<? extends GradleBuild> getEditableBuilds(@NotNull GradleBuild build, @NotNull GradleVersion version) {
    if (GradleVersionUtil.isGradleAtLeast(version, "8.0")) {
      DomainObjectSet<? extends GradleBuild> builds = build.getEditableBuilds();
      if (builds.isEmpty()) {
        return build.getIncludedBuilds();
      }
      else {
        return builds;
      }
    }
    else {
      return build.getIncludedBuilds();
    }
  }

  private static void setupIncludedBuildsHierarchy(List<Build> builds, Set<GradleBuild> gradleBuilds) {
    Set<Build> updatedBuilds = new HashSet<>();
    Map<File, Build> rootDirsToBuilds = new HashMap<>();
    for (Build build : builds) {
      rootDirsToBuilds.put(build.getBuildIdentifier().getRootDir(), build);
    }

    for (GradleBuild gradleBuild : gradleBuilds) {
      Build build = rootDirsToBuilds.get(gradleBuild.getBuildIdentifier().getRootDir());
      if (build == null) {
        continue;
      }

      for (GradleBuild includedGradleBuild : gradleBuild.getIncludedBuilds()) {
        Build buildToUpdate = rootDirsToBuilds.get(includedGradleBuild.getBuildIdentifier().getRootDir());
        if (buildToUpdate instanceof DefaultBuild && updatedBuilds.add(buildToUpdate)) {
          ((DefaultBuild)buildToUpdate).setParentBuildIdentifier(
            new DefaultBuildIdentifier(gradleBuild.getBuildIdentifier().getRootDir()));
        }
      }
    }
  }

  private void forEachModelFetchPhase(@NotNull BiConsumer<GradleModelFetchPhase, List<ProjectImportModelProvider>> consumer) {
    myModelProviders.stream()
      .collect(Collectors.groupingBy(it -> it.getPhase())).entrySet().stream()
      .sorted(Map.Entry.comparingByKey())
      .forEachOrdered(it -> consumer.accept(it.getKey(), it.getValue()));
  }

  private void addModels(@NotNull BuildController controller, @NotNull GradleBuild gradleBuild) {
    try {
      forEachModelFetchPhase((phase, modelProviders) -> {
        myTelemetry.runWithSpan("AddProjectModels", span -> {
          span.setAttribute("phase", phase.name());
          for (BasicGradleProject gradleProject : gradleBuild.getProjects()) {
            for (ProjectImportModelProvider modelProvider : modelProviders) {
              addProjectModels(controller, gradleProject, modelProvider);
            }
          }
        });
        myTelemetry.runWithSpan("AddBuildModels", span -> {
          span.setAttribute("phase", phase.name());
          for (ProjectImportModelProvider modelProvider : modelProviders) {
            addBuildModels(controller, gradleBuild, modelProvider);
          }
        });
      });
    }
    catch (Exception e) {
      // do not fail project import in a preview mode
      if (!myIsPreviewMode) {
        throw new ExternalSystemException(e);
      }
    }
  }

  private void addProjectModels(
    @NotNull BuildController controller,
    @NotNull BasicGradleProject gradleProject,
    @NotNull ProjectImportModelProvider modelProvider
  ) {
    myTelemetry.runWithSpan(modelProvider.getName(), span -> {
      modelProvider.populateProjectModels(controller, gradleProject, new ProjectImportModelProvider.ProjectModelConsumer() {
        @Override
        public void consume(@NotNull Object object, @NotNull Class<?> clazz) {
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
      modelProvider.populateBuildModels(controller, gradleBuild, new ProjectImportModelProvider.BuildModelConsumer() {
        @Override
        public void consumeProjectModel(@NotNull ProjectModel projectModel, @NotNull Object object, @NotNull Class<?> clazz) {
          addProjectModel(projectModel, object, clazz);
        }

        @Override
        public void consume(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class<?> clazz) {
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
