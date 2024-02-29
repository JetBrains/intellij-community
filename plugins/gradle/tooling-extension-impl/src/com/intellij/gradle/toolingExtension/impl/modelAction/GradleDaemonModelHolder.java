// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.modelSerialization.ModelConverter;
import io.opentelemetry.context.Context;
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
import org.jetbrains.plugins.gradle.model.DefaultBuild;
import org.jetbrains.plugins.gradle.model.DefaultBuildController;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer;

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
 *   <li>Consumes data on the Idea side into the {@link org.jetbrains.plugins.gradle.service.buildActionRunner.GradleIdeaModelHolder}.
 *   This data can be accessed by the {@link org.jetbrains.plugins.gradle.service.project.ProjectResolverContext}
 *   in the {@link org.jetbrains.plugins.gradle.service.project.GradleProjectResolver} and their extensions.</li>
 * </ul>
 *
 * @see org.jetbrains.plugins.gradle.service.buildActionRunner.GradleIdeaModelHolder
 * @see org.gradle.tooling.BuildActionExecuter.Builder#projectsLoaded
 * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished
 * @see org.gradle.tooling.BuildActionExecuter#run
 */
@ApiStatus.Internal
public class GradleDaemonModelHolder {

  private final @NotNull ModelConverter myModelConverter;

  private final @NotNull GradleBuild myRootGradleBuild;
  private final @NotNull Collection<? extends GradleBuild> myNestedGradleBuilds;

  private final @NotNull BuildEnvironment myBuildEnvironment;

  private final @NotNull BlockingQueue<Future<DefaultBuild>> myConvertedRootBuild = new LinkedBlockingQueue<>();
  private final @NotNull BlockingQueue<Future<Collection<DefaultBuild>>> myConvertedNestedBuilds = new LinkedBlockingQueue<>();
  private final @NotNull BlockingQueue<Future<ConvertedModel>> myConvertedModelQueue = new LinkedBlockingQueue<>();

  public GradleDaemonModelHolder(
    @NotNull ExecutorService converterExecutor,
    @NotNull ModelConverter modelConverter,
    @NotNull GradleBuild rootGradleBuild,
    @NotNull Collection<? extends GradleBuild> nestedGradleBuilds,
    @NotNull BuildEnvironment buildEnvironment
  ) {
    myModelConverter = modelConverter;
    myRootGradleBuild = rootGradleBuild;
    myNestedGradleBuilds = nestedGradleBuilds;
    myBuildEnvironment = buildEnvironment;
    submitTask(converterExecutor, myConvertedRootBuild, () -> DefaultBuild.convertGradleBuild(rootGradleBuild));
    submitTask(converterExecutor, myConvertedNestedBuilds, () -> convertNestedGradleBuilds(nestedGradleBuilds));
  }

  public @NotNull Collection<? extends GradleBuild> getGradleBuilds() {
    List<GradleBuild> gradleBuilds = new ArrayList<>();
    gradleBuilds.add(myRootGradleBuild);
    gradleBuilds.addAll(myNestedGradleBuilds);
    return gradleBuilds;
  }

  public @NotNull BuildController createBuildController(@NotNull BuildController parentController) {
    return new DefaultBuildController(parentController, myRootGradleBuild);
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
        submitTask(converterExecutor, myConvertedModelQueue, () -> {
          Object convertedModel = myModelConverter.convert(model);
          return new ConvertedModel(modelId, convertedModel);
        });
      }
    };
  }

  private static <T> void submitTask(
    @NotNull ExecutorService executor,
    @NotNull BlockingQueue<Future<T>> queue,
    @NotNull Callable<T> task
  ) {
    Future<T> convertedModelFuture = Context.current()
      .wrap(executor)
      .submit(task);
    queue.add(convertedModelFuture);
  }

  public @NotNull GradleModelHolderState pollPendingState() {
    DefaultBuild rootBuild = pollPendingConvertedRootBuild();
    Collection<DefaultBuild> nestedBuilds = pollPendingConvertedNestedBuilds();
    Map<GradleModelId, Object> models = pollAllPendingConvertedModels();
    return new GradleModelHolderState(rootBuild, nestedBuilds, myBuildEnvironment, models);
  }

  private @Nullable DefaultBuild pollPendingConvertedRootBuild() {
    return poolPendingModel(myConvertedRootBuild);
  }

  private @NotNull Collection<DefaultBuild> pollPendingConvertedNestedBuilds() {
    Collection<DefaultBuild> builds = poolPendingModel(myConvertedNestedBuilds);
    return builds == null ? Collections.emptyList() : builds;
  }

  private @NotNull Map<GradleModelId, Object> pollAllPendingConvertedModels() {
    Map<GradleModelId, Object> models = new LinkedHashMap<>();
    ConvertedModel model = poolPendingModel(myConvertedModelQueue);
    while (model != null) {
      models.put(model.myId, model.myModel);
      model = poolPendingModel(myConvertedModelQueue);
    }
    return models;
  }

  private static <T> @Nullable T poolPendingModel(@NotNull BlockingQueue<Future<T>> modelQueue) {
    try {
      Future<T> future = modelQueue.poll();
      if (future == null) {
        return null;
      }
      return future.get();
    }
    catch (InterruptedException | ExecutionException ignored) {
      return null;
    }
  }

  private static @NotNull Collection<DefaultBuild> convertNestedGradleBuilds(
    @NotNull Collection<? extends GradleBuild> nestedGradleBuilds
  ) {
    List<DefaultBuild> nestedBuilds = new ArrayList<>();
    for (GradleBuild gradleBuild : nestedGradleBuilds) {
      DefaultBuild build = DefaultBuild.convertGradleBuild(gradleBuild);
      nestedBuilds.add(build);
    }
    setupNestedBuildHierarchy(nestedBuilds, nestedGradleBuilds);
    return nestedBuilds;
  }

  private static void setupNestedBuildHierarchy(
    @NotNull Collection<DefaultBuild> builds,
    @NotNull Collection<? extends GradleBuild> gradleBuilds
  ) {
    Set<DefaultBuild> updatedBuilds = new HashSet<>();
    Map<File, DefaultBuild> rootDirsToBuilds = new HashMap<>();
    for (DefaultBuild build : builds) {
      BuildIdentifier buildIdentifier = build.getBuildIdentifier();
      rootDirsToBuilds.put(buildIdentifier.getRootDir(), build);
    }

    for (GradleBuild gradleBuild : gradleBuilds) {
      BuildIdentifier buildIdentifier = gradleBuild.getBuildIdentifier();
      DefaultBuild build = rootDirsToBuilds.get(buildIdentifier.getRootDir());
      if (build == null) {
        continue;
      }

      for (GradleBuild includedGradleBuild : gradleBuild.getIncludedBuilds()) {
        DefaultBuild buildToUpdate = rootDirsToBuilds.get(includedGradleBuild.getBuildIdentifier().getRootDir());
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
