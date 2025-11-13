// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializerConverter;
import com.intellij.gradle.toolingExtension.impl.util.GradleExecutorServiceUtil;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.DefaultGradleLightBuild;
import org.jetbrains.plugins.gradle.model.DefaultBuildController;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer;

import java.util.*;
import java.util.concurrent.*;

import static org.jetbrains.plugins.gradle.model.DefaultGradleLightBuild.convertGradleBuilds;

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

  private final @NotNull GradleVersion myGradleVersion;

  private final @NotNull BlockingQueue<Future<List<DefaultGradleLightBuild>>> myConvertedBuilds = new LinkedBlockingQueue<>();
  private final @NotNull BlockingQueue<Future<ConvertedModel>> myConvertedModelQueue = new LinkedBlockingQueue<>();

  public GradleDaemonModelHolder(
    @NotNull ExecutorService converterExecutor,
    @NotNull ToolingSerializerConverter serializer,
    @NotNull GradleBuild rootGradleBuild,
    @NotNull Collection<? extends GradleBuild> nestedGradleBuilds,
    @NotNull GradleVersion gradleVersion
  ) {
    mySerializer = serializer;
    myRootGradleBuild = rootGradleBuild;
    myNestedGradleBuilds = nestedGradleBuilds;
    myGradleVersion = gradleVersion;

    GradleExecutorServiceUtil.submitTask(converterExecutor, myConvertedBuilds, () -> {
      List<GradleBuild> gradleBuilds = new ArrayList<>();
      gradleBuilds.add(myRootGradleBuild);
      gradleBuilds.addAll(myNestedGradleBuilds);
      return convertGradleBuilds(gradleBuilds, myGradleVersion);
    });
  }

  public @NotNull Collection<? extends GradleBuild> getGradleBuilds() {
    List<GradleBuild> gradleBuilds = new ArrayList<>();
    gradleBuilds.add(myRootGradleBuild);
    gradleBuilds.addAll(myNestedGradleBuilds);
    return gradleBuilds;
  }

  public @NotNull BuildController createBuildController(@NotNull BuildController parentController) {
    return new DefaultBuildController(parentController, myRootGradleBuild, myGradleVersion);
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
    List<DefaultGradleLightBuild> builds = pollPendingConvertedNestedBuilds();
    DefaultGradleLightBuild rootBuild = builds.isEmpty() ? null : builds.get(0);
    List<DefaultGradleLightBuild> nestedBuilds = builds.size() > 1 ? builds.subList(1, builds.size()) : Collections.emptyList();

    Map<GradleModelId, Object> models = pollAllPendingConvertedModels();
    return new GradleModelHolderState(rootBuild, new ArrayList<>(nestedBuilds), models);
  }

  private @NotNull List<DefaultGradleLightBuild> pollPendingConvertedNestedBuilds() {
    List<DefaultGradleLightBuild> builds = GradleExecutorServiceUtil.firstOrNull(myConvertedBuilds);
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

  private static class ConvertedModel {

    private final @NotNull GradleModelId myId;
    private final @NotNull Object myModel;

    private ConvertedModel(@NotNull GradleModelId id, @NotNull Object model) {
      myId = id;
      myModel = model;
    }
  }
}
