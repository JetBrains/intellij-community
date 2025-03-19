// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializerConverter;
import com.intellij.gradle.toolingExtension.impl.util.GradleExecutorServiceUtil;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultGradleLightBuild;
import org.jetbrains.plugins.gradle.model.DefaultBuildController;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildEnvironment;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/**
 * Holder for the Gradle models that consumes and holds data on the Gradle Daemon side.
 * <p>
 * Gradle model transportation steps:
 * <ul>
 *   <li>Collect data into this holder during {@link GradleModelFetchAction}.</li>
 *   <li>Form {@link GradleModelHolderState} for each Gradle sync phase.</li>
 *   <li>Transport state by the Gradle build-in transportation methods.</li>
 *   <ul>
 *     <li>Using two-phased Gradle executor with {@link org.gradle.tooling.IntermediateResultHandler}.</li>
 *     <li>Using one-phased Gradle executor with {@link org.gradle.tooling.ResultHandler}.</li>
 *   </ul>
 *   <li>Consumes data on the Idea side into the {@link org.jetbrains.plugins.gradle.service.modelAction.GradleIdeaModelHolder}.
 *   This data can be accessed by the {@link org.jetbrains.plugins.gradle.service.project.ProjectResolverContext}
 *   in the {@link org.jetbrains.plugins.gradle.service.project.GradleProjectResolver} and their extensions.</li>
 * </ul>
 *
 * @see org.jetbrains.plugins.gradle.service.modelAction.GradleIdeaModelHolder
 * @see org.gradle.tooling.BuildActionExecuter.Builder#projectsLoaded
 * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished
 * @see org.gradle.tooling.BuildActionExecuter#run
 */
@ApiStatus.Internal
public class GradleDaemonModelHolder {

  private final @NotNull ToolingSerializerConverter mySerializer;

  private final @NotNull GradleBuild myRootGradleBuild;
  private final @NotNull Collection<? extends GradleBuild> myNestedGradleBuilds;

  private final @NotNull BuildEnvironment myBuildEnvironment;

  private final @NotNull BlockingQueue<Future<DefaultGradleLightBuild>> myConvertedRootBuild = new LinkedBlockingQueue<>();
  private final @NotNull BlockingQueue<Future<Collection<DefaultGradleLightBuild>>> myConvertedNestedBuilds = new LinkedBlockingQueue<>();
  private final @NotNull BlockingQueue<Future<ConvertedModel>> myConvertedModelQueue = new LinkedBlockingQueue<>();
  private final @NotNull BlockingQueue<Future<BuildEnvironment>> myConvertedBuildEnvironment = new LinkedBlockingQueue<>();

  public GradleDaemonModelHolder(
    @NotNull ExecutorService converterExecutor,
    @NotNull ToolingSerializerConverter serializer,
    @NotNull GradleBuild rootGradleBuild,
    @NotNull Collection<? extends GradleBuild> nestedGradleBuilds,
    @NotNull BuildEnvironment buildEnvironment
  ) {
    mySerializer = serializer;
    myRootGradleBuild = rootGradleBuild;
    myNestedGradleBuilds = nestedGradleBuilds;
    myBuildEnvironment = buildEnvironment;
    GradleExecutorServiceUtil.submitTask(converterExecutor, myConvertedRootBuild, () -> {
      return DefaultGradleLightBuild.convertGradleBuild(rootGradleBuild);
    });
    GradleExecutorServiceUtil.submitTask(converterExecutor, myConvertedNestedBuilds, () -> {
      return convertNestedGradleBuilds(nestedGradleBuilds);
    });
    GradleExecutorServiceUtil.submitTask(converterExecutor, myConvertedBuildEnvironment, () -> {
      return InternalBuildEnvironment.convertBuildEnvironment(myBuildEnvironment);
    });
  }

  public @NotNull Collection<? extends GradleBuild> getGradleBuilds() {
    List<GradleBuild> gradleBuilds = new ArrayList<>();
    gradleBuilds.add(myRootGradleBuild);
    gradleBuilds.addAll(myNestedGradleBuilds);
    return gradleBuilds;
  }

  public @NotNull BuildController createBuildController(@NotNull BuildController parentController) {
    return new DefaultBuildController(parentController, myRootGradleBuild, myBuildEnvironment);
  }

  public @NotNull GradleModelConsumer createModelConsumer(@NotNull ExecutorService converterExecutor) {
    return new GradleModelConsumer() {
      @Override
      public void consumeBuildModel(@NotNull BuildModel buildModel, @NotNull Object object, @NotNull Class<?> clazz) {
        consumeModel(object, GradleModelId.createBuildModelId(buildModel, clazz));
      }

      @Override
      public void consumeProjectModel(@NotNull BasicGradleProject projectModel, @NotNull Object object, @NotNull Class<?> clazz) {
        consumeModel(object, GradleModelId.createProjectModelId(projectModel, clazz));
      }

      private void consumeModel(@NotNull Object model, @NotNull GradleModelId modelId) {
        GradleExecutorServiceUtil.submitTask(converterExecutor, myConvertedModelQueue, () -> {
          Object convertedModel = mySerializer.convert(model);
          return new ConvertedModel(modelId, convertedModel);
        });
      }
    };
  }

  public @NotNull GradleModelHolderState pollPendingState() {
    DefaultGradleLightBuild rootBuild = pollPendingConvertedRootBuild();
    Collection<DefaultGradleLightBuild> nestedBuilds = pollPendingConvertedNestedBuilds();
    Map<GradleModelId, Object> models = pollAllPendingConvertedModels();
    BuildEnvironment buildEnvironment = pollPendingBuildEnvironment();
    return new GradleModelHolderState(rootBuild, nestedBuilds, buildEnvironment, models);
  }

  private @Nullable DefaultGradleLightBuild pollPendingConvertedRootBuild() {
    return GradleExecutorServiceUtil.poolPendingResult(myConvertedRootBuild);
  }

  private @NotNull Collection<DefaultGradleLightBuild> pollPendingConvertedNestedBuilds() {
    Collection<DefaultGradleLightBuild> builds = GradleExecutorServiceUtil.poolPendingResult(myConvertedNestedBuilds);
    return builds == null ? Collections.emptyList() : builds;
  }

  private @NotNull Map<GradleModelId, Object> pollAllPendingConvertedModels() {
    List<ConvertedModel> models = GradleExecutorServiceUtil.pollAllPendingResults(myConvertedModelQueue);
    Map<GradleModelId, Object> modelMap = new LinkedHashMap<>();
    for (ConvertedModel convertedModel : models) {
      modelMap.put(convertedModel.myId, convertedModel.myModel);
    }
    return modelMap;
  }

  private @Nullable BuildEnvironment pollPendingBuildEnvironment() {
    return GradleExecutorServiceUtil.poolPendingResult(myConvertedBuildEnvironment);
  }

  private static @NotNull Collection<DefaultGradleLightBuild> convertNestedGradleBuilds(
    @NotNull Collection<? extends GradleBuild> nestedGradleBuilds
  ) {
    List<DefaultGradleLightBuild> nestedBuilds = new ArrayList<>();
    for (GradleBuild gradleBuild : nestedGradleBuilds) {
      DefaultGradleLightBuild build = DefaultGradleLightBuild.convertGradleBuild(gradleBuild);
      nestedBuilds.add(build);
    }
    setupNestedBuildHierarchy(nestedBuilds, nestedGradleBuilds);
    return nestedBuilds;
  }

  private static void setupNestedBuildHierarchy(
    @NotNull Collection<DefaultGradleLightBuild> builds,
    @NotNull Collection<? extends GradleBuild> gradleBuilds
  ) {
    Set<DefaultGradleLightBuild> updatedBuilds = new HashSet<>();
    Map<File, DefaultGradleLightBuild> rootDirsToBuilds = new HashMap<>();
    for (DefaultGradleLightBuild build : builds) {
      BuildIdentifier buildIdentifier = build.getBuildIdentifier();
      rootDirsToBuilds.put(buildIdentifier.getRootDir(), build);
    }

    for (GradleBuild gradleBuild : gradleBuilds) {
      BuildIdentifier buildIdentifier = gradleBuild.getBuildIdentifier();
      DefaultGradleLightBuild build = rootDirsToBuilds.get(buildIdentifier.getRootDir());
      if (build == null) {
        continue;
      }

      for (GradleBuild includedGradleBuild : gradleBuild.getIncludedBuilds()) {
        DefaultGradleLightBuild buildToUpdate = rootDirsToBuilds.get(includedGradleBuild.getBuildIdentifier().getRootDir());
        if (buildToUpdate != null && updatedBuilds.add(buildToUpdate)) {
          buildToUpdate.setParentBuildIdentifier(new DefaultBuildIdentifier(buildIdentifier.getRootDir()));
        }
      }
    }
  }

  private static class ConvertedModel {

    private final @NotNull GradleModelId myId;
    private final @NotNull Object myModel;

    private ConvertedModel(@NotNull GradleModelId id, @NotNull Object model) {
      myId = id;
      myModel = model;
    }
  }
}
