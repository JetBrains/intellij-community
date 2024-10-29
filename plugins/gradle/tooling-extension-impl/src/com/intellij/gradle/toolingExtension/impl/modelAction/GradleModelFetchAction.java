// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.model.utilTurnOffDefaultTasksModel.TurnOffDefaultTasks;
import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializerConverter;
import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry;
import com.intellij.gradle.toolingExtension.impl.telemetry.GradleTracingContext;
import com.intellij.gradle.toolingExtension.impl.telemetry.TelemetryHolder;
import com.intellij.gradle.toolingExtension.impl.util.GradleExecutorServiceUtil;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.util.ReflectionUtilRt;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.TargetTypeProvider;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class GradleModelFetchAction implements BuildAction<GradleModelHolderState>, Serializable {

  private final NavigableSet<GradleModelFetchPhase> myModelFetchPhases = new TreeSet<>(Arrays.asList(GradleModelFetchPhase.values()));
  private final Map<GradleModelFetchPhase, Set<ProjectImportModelProvider>> myModelProviders = new LinkedHashMap<>();
  private final Set<Class<?>> myTargetTypes = new LinkedHashSet<>();

  private boolean myUseProjectsLoadedPhase = false;
  private boolean myUseStreamedValues = false;

  private @Nullable GradleTracingContext myTracingContext = null;

  private transient boolean myProjectLoadedAction = false;

  private transient @Nullable GradleDaemonModelHolder myModels = null;

  public GradleModelFetchAction addProjectImportModelProviders(
    @NotNull Collection<? extends ProjectImportModelProvider> providers
  ) {
    for (ProjectImportModelProvider provider : providers) {
      GradleModelFetchPhase phase = provider.getPhase();
      Set<ProjectImportModelProvider> modelProviders = myModelProviders.computeIfAbsent(phase, __ -> new LinkedHashSet<>());
      modelProviders.add(provider);
    }
    return this;
  }

  public Set<Class<?>> getModelProvidersClasses() {
    Set<Class<?>> result = new LinkedHashSet<>();
    for (Set<ProjectImportModelProvider> modelProviders : myModelProviders.values()) {
      for (ProjectImportModelProvider provider : modelProviders) {
        result.add(provider.getClass());
      }
    }
    return result;
  }

  public void addTargetTypes(@NotNull Set<Class<?>> targetTypes) {
    myTargetTypes.addAll(targetTypes);
  }

  public boolean isUseProjectsLoadedPhase() {
    return myUseProjectsLoadedPhase;
  }

  public void setUseProjectsLoadedPhase(boolean useProjectsLoadedPhase) {
    myUseProjectsLoadedPhase = useProjectsLoadedPhase;
  }

  public boolean isUseStreamedValues() {
    return myUseStreamedValues;
  }

  public void setUseStreamedValues(boolean useStreamedValues) {
    myUseStreamedValues = useStreamedValues;
  }

  public void setTracingContext(@NotNull GradleTracingContext tracingContext) {
    myTracingContext = tracingContext;
  }

  @Override
  public @NotNull GradleModelHolderState execute(@NotNull BuildController controller) {
    configureAdditionalTypes(controller);
    return GradleExecutorServiceUtil.withSingleThreadExecutor("idea-tooling-model-converter", converterExecutor -> {
      return withOpenTelemetry(telemetry -> {
        return telemetry.callWithSpan("ProjectImportAction", __ -> {
          return doExecute(controller, converterExecutor, telemetry);
        });
      });
    });
  }

  private GradleModelHolderState withOpenTelemetry(@NotNull Function<GradleOpenTelemetry, GradleModelHolderState> action) {
    GradleTracingContext tracingContext = myTracingContext;
    if (tracingContext == null) {
      GradleOpenTelemetry noopTelemetry = new GradleOpenTelemetry();
      return action.apply(noopTelemetry);
    }

    GradleOpenTelemetry telemetry = new GradleOpenTelemetry();
    telemetry.start(tracingContext);
    GradleModelHolderState state;
    try {
      state = action.apply(telemetry);
    }
    catch (Throwable exception) {
      telemetry.shutdown();
      throw exception;
    }
    TelemetryHolder holder = telemetry.shutdown();
    return state.withOpenTelemetryTraces(holder);
  }

  private @NotNull GradleModelHolderState doExecute(
    @NotNull BuildController controller,
    @NotNull ExecutorService converterExecutor,
    @NotNull GradleOpenTelemetry telemetry
  ) {
    myProjectLoadedAction = myModels == null && myUseProjectsLoadedPhase;

    if (myProjectLoadedAction || !myUseProjectsLoadedPhase) {
      myModels = initAction(controller, converterExecutor, telemetry);
    }

    assert myModels != null;

    executeAction(controller, converterExecutor, telemetry, myModels);

    if (myProjectLoadedAction) {
      telemetry.runWithSpan("TurnOffDefaultTasks", __ ->
        controller.getModel(TurnOffDefaultTasks.class)
      );
    }

    return myModels.pollPendingState();
  }

  private void configureAdditionalTypes(BuildController controller) {
    if (myTargetTypes.isEmpty()) return;
    try {
      ProtocolToModelAdapter modelAdapter =
        ReflectionUtilRt.getField(controller.getClass(), controller, ProtocolToModelAdapter.class, "adapter");
      if (modelAdapter == null) return;
      TargetTypeProvider typeProvider =
        ReflectionUtilRt.getField(ProtocolToModelAdapter.class, modelAdapter, TargetTypeProvider.class, "targetTypeProvider");
      if (typeProvider == null) return;
      //noinspection unchecked
      Map<String, Class<?>> targetTypes =
        ReflectionUtilRt.getField(typeProvider.getClass(), typeProvider, Map.class, "configuredTargetTypes");
      if (targetTypes == null) return;
      for (Class<?> targetType : myTargetTypes) {
        targetTypes.put(targetType.getCanonicalName(), targetType);
      }
    }
    catch (Exception ignore) {
    }
  }

  private static @NotNull GradleDaemonModelHolder initAction(
    @NotNull BuildController controller,
    @NotNull ExecutorService converterExecutor,
    @NotNull GradleOpenTelemetry telemetry
  ) {
    GradleBuild mainGradleBuild = telemetry.callWithSpan("GetMainGradleBuild", __ ->
      controller.getBuildModel()
    );
    BuildEnvironment buildEnvironment = telemetry.callWithSpan("GetBuildEnvironment", __ ->
      controller.getModel(BuildEnvironment.class)
    );
    Collection<? extends GradleBuild> nestedGradleBuilds = telemetry.callWithSpan("GetNestedGradleBuilds", __ ->
      getNestedBuilds(buildEnvironment, mainGradleBuild)
    );
    ToolingSerializerConverter serializer = telemetry.callWithSpan("GetToolingModelConverter", __ ->
      new ToolingSerializerConverter(controller, telemetry)
    );
    return telemetry.callWithSpan("InitModelConsumer", __ ->
      new GradleDaemonModelHolder(converterExecutor, serializer, mainGradleBuild, nestedGradleBuilds, buildEnvironment)
    );
  }

  private static Collection<? extends GradleBuild> getNestedBuilds(@NotNull BuildEnvironment buildEnvironment, @NotNull GradleBuild build) {
    GradleVersion gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
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
    if (GradleVersionUtil.isGradleOlderThan(version, "8.0")) {
      return build.getIncludedBuilds();
    }
    DomainObjectSet<? extends GradleBuild> builds = build.getEditableBuilds();
    if (builds.isEmpty()) {
      return build.getIncludedBuilds();
    }
    else {
      return builds;
    }
  }

  private void executeAction(
    @NotNull BuildController controller,
    @NotNull ExecutorService converterExecutor,
    @NotNull GradleOpenTelemetry telemetry,
    @NotNull GradleDaemonModelHolder models
  ) {
    BuildController buildController = models.createBuildController(controller);
    GradleModelConsumer modelConsumer = models.createModelConsumer(converterExecutor);
    Collection<? extends GradleBuild> gradleBuilds = models.getGradleBuilds();

    try {
      getModelFetchPhases().forEach(phase -> {
        telemetry.runWithSpan(phase.name(), __ -> {
          Set<ProjectImportModelProvider> modelProviders = myModelProviders.getOrDefault(phase, Collections.emptySet());
          populateModels(buildController, telemetry, modelConsumer, gradleBuilds, modelProviders);
          sendPendingState(buildController, telemetry, models, phase);
        });
      });
    }
    catch (Exception e) {
      throw new ExternalSystemException(e);
    }
  }

  private static void populateModels(
    @NotNull BuildController controller,
    @NotNull GradleOpenTelemetry telemetry,
    @NotNull GradleModelConsumer modelConsumer,
    @NotNull Collection<? extends GradleBuild> gradleBuilds,
    @NotNull Collection<ProjectImportModelProvider> modelProviders
  ) {
    telemetry.runWithSpan("PopulateModels", __ -> {
      for (GradleBuild gradleBuild : gradleBuilds) {
        for (BasicGradleProject gradleProject : gradleBuild.getProjects()) {
          for (ProjectImportModelProvider modelProvider : modelProviders) {
            telemetry.runWithSpan(modelProvider.getName(), span -> {
              span.setAttribute("project-name", gradleProject.getName());
              span.setAttribute("build-name", gradleBuild.getBuildIdentifier().getRootDir().getName());
              span.setAttribute("model-type", "ProjectModel");
              modelProvider.populateProjectModels(controller, gradleProject, modelConsumer);
            });
          }
        }
        for (ProjectImportModelProvider modelProvider : modelProviders) {
          telemetry.runWithSpan(modelProvider.getName(), span -> {
            span.setAttribute("build-name", gradleBuild.getBuildIdentifier().getRootDir().getName());
            span.setAttribute("model-type", "BuildModel");
            modelProvider.populateBuildModels(controller, gradleBuild, modelConsumer);
          });
        }
      }
      for (ProjectImportModelProvider modelProvider : modelProviders) {
        telemetry.runWithSpan(modelProvider.getName(), span -> {
          span.setAttribute("model-type", "GradleModel");
          modelProvider.populateModels(controller, gradleBuilds, modelConsumer);
        });
      }
    });
  }

  private void sendPendingState(
    @NotNull BuildController controller,
    @NotNull GradleOpenTelemetry telemetry,
    @NotNull GradleDaemonModelHolder models,
    @NotNull GradleModelFetchPhase phase
  ) {
    telemetry.runWithSpan("SendPendingState", span -> {
      span.setAttribute("use-streamed-values", myUseStreamedValues);
      if (myUseStreamedValues) {
        GradleModelHolderState state = models.pollPendingState();
        GradleModelHolderState phasedState = state.withPhase(phase);
        controller.send(phasedState);
      }
    });
  }

  private @NotNull SortedSet<GradleModelFetchPhase> getModelFetchPhases() {
    if (!myUseProjectsLoadedPhase) {
      return myModelFetchPhases;
    }
    if (myProjectLoadedAction) {
      return getProjectLoadedModelFetchPhases();
    }
    return getBuildFinishedModelFetchPhases();
  }

  /**
   * @see org.gradle.tooling.BuildActionExecuter.Builder#projectsLoaded
   */
  public @NotNull SortedSet<GradleModelFetchPhase> getProjectLoadedModelFetchPhases() {
    return myModelFetchPhases.headSet(GradleModelFetchPhase.PROJECT_LOADED_PHASE, true);
  }

  /**
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished
   */
  public @NotNull SortedSet<GradleModelFetchPhase> getBuildFinishedModelFetchPhases() {
    return myModelFetchPhases.tailSet(GradleModelFetchPhase.PROJECT_LOADED_PHASE, false);
  }
}
