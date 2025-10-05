// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.model.utilTurnOffDefaultTasksModel.TurnOffDefaultTasks;
import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializerConverter;
import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry;
import com.intellij.gradle.toolingExtension.impl.util.GradleExecutorServiceUtil;
import com.intellij.gradle.toolingExtension.impl.util.collectionUtil.GradleCollections;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.util.ReflectionUtilRt;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.TargetTypeProvider;
import org.gradle.tooling.model.DomainObjectSet;
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

  private final @NotNull String myGradleVersion;

  private final NavigableMap<GradleModelFetchPhase, Set<ProjectImportModelProvider>> myModelProviders = new TreeMap<>();
  private final Set<Class<?>> myTargetTypes = new LinkedHashSet<>();

  private boolean myUseProjectsLoadedPhase = false;
  private boolean myUseStreamedValues = false;

  private transient boolean myProjectLoadedAction = false;
  private transient @Nullable GradleDaemonModelHolder myModels = null;

  public GradleModelFetchAction(@NotNull GradleVersion version) {
    myGradleVersion = version.getVersion();
  }

  private @NotNull GradleVersion getGradleVersion() {
    return GradleVersion.version(myGradleVersion);
  }

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

  private @NotNull GradleModelHolderState doExecute(
    @NotNull BuildController controller,
    @NotNull ExecutorService converterExecutor,
    @NotNull GradleOpenTelemetry telemetry
  ) {
    myProjectLoadedAction = myModels == null && myUseProjectsLoadedPhase;

    if (myProjectLoadedAction || !myUseProjectsLoadedPhase) {
      myModels = telemetry.callWithSpan("InitAction", __ ->
        initAction(controller, converterExecutor, telemetry, getGradleVersion())
      );
    }

    assert myModels != null;

    telemetry.runWithSpan("ExecuteAction", __ ->
      executeAction(controller, converterExecutor, telemetry, myModels)
    );

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
    @NotNull GradleOpenTelemetry telemetry,
    @NotNull GradleVersion gradleVersion
  ) {
    GradleBuild mainGradleBuild = telemetry.callWithSpan("GetMainGradleBuild", __ ->
      controller.getBuildModel()
    );
    Collection<? extends GradleBuild> nestedGradleBuilds = telemetry.callWithSpan("GetNestedGradleBuilds", __ ->
      getNestedBuilds(mainGradleBuild, gradleVersion)
    );
    ToolingSerializerConverter serializer = telemetry.callWithSpan("GetToolingModelConverter", __ ->
      new ToolingSerializerConverter(controller, telemetry)
    );
    return telemetry.callWithSpan("InitModelConsumer", __ ->
      new GradleDaemonModelHolder(converterExecutor, serializer, mainGradleBuild, nestedGradleBuilds, gradleVersion)
    );
  }

  private static Collection<? extends GradleBuild> getNestedBuilds(@NotNull GradleBuild build, @NotNull GradleVersion gradleVersion) {
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
        telemetry.runWithSpan(phase.getName(), __ -> {
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
    for (ProjectImportModelProvider modelProvider : modelProviders) {
      telemetry.runWithSpan(modelProvider.getName(), __ -> {
        modelProvider.populateModels(controller, gradleBuilds, modelConsumer);
      });
    }
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

  private @NotNull Collection<GradleModelFetchPhase> getModelFetchPhases() {
    if (!myUseProjectsLoadedPhase) {
      return myModelProviders.keySet();
    }
    if (myProjectLoadedAction) {
      return getProjectLoadedModelFetchPhases();
    }
    return getBuildFinishedModelFetchPhases();
  }

  public @NotNull List<GradleModelFetchPhase> getProjectLoadedModelFetchPhases() {
    return GradleCollections.filter(myModelProviders.keySet(), it -> it instanceof GradleModelFetchPhase.ProjectLoaded);
  }

  public @NotNull List<GradleModelFetchPhase> getBuildFinishedModelFetchPhases() {
    return GradleCollections.filter(myModelProviders.keySet(), it -> it instanceof GradleModelFetchPhase.BuildFinished);
  }

  private static @NotNull GradleModelHolderState withOpenTelemetry(
    @NotNull Function<GradleOpenTelemetry, GradleModelHolderState> action
  ) {
    GradleOpenTelemetry telemetry = new GradleOpenTelemetry();
    try {
      return action.apply(telemetry);
    }
    catch (Throwable exception) {
      telemetry.shutdown();
      throw exception;
    }
  }
}
