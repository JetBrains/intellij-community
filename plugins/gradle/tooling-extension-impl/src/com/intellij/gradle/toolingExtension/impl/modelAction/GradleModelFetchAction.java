// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.util.GradleClassLoaderUtil;
import com.intellij.gradle.toolingExtension.impl.model.dslBaseScriptModel.GradleDslBaseScriptModelHolder;
import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializerConverter;
import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry;
import com.intellij.gradle.toolingExtension.impl.util.GradleExecutorServiceUtil;
import com.intellij.gradle.toolingExtension.impl.util.collectionUtil.GradleCollections;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.gradle.toolingExtension.util.GradleVersionSpecificsUtil;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.util.ReflectionUtilRt;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.TargetTypeProvider;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultBuildController;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

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
      return GradleOpenTelemetry.callWithSpan("ProjectImportAction", __ -> {
        return doExecute(controller, converterExecutor);
      });
    });
  }

  private @NotNull GradleModelHolderState doExecute(
    @NotNull BuildController buildController,
    @NotNull ExecutorService converterExecutor
  ) {
    myProjectLoadedAction = myModels == null && myUseProjectsLoadedPhase;

    if (myProjectLoadedAction || !myUseProjectsLoadedPhase) {
      if (myUseStreamedValues && GradleVersionSpecificsUtil.isBaseScriptModelSupported(getGradleVersion())) {
        GradleOpenTelemetry.runWithSpan("SendBaseScriptModelState", __ ->
          sendBaseScriptModelState(buildController)
        );
      }
      myModels = GradleOpenTelemetry.callWithSpan("InitAction", __ ->
        initAction(buildController, converterExecutor, getGradleVersion())
      );
    }

    GradleDaemonModelHolder models = myModels;
    assert models != null;

    GradleOpenTelemetry.runWithSpan("ExecuteAction", __ ->
      executeAction(buildController, converterExecutor, models)
    );

    return models.pollPendingState();
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

  private static void sendBaseScriptModelState(@NotNull BuildController buildController) {
    GradleModelControllerImpl modelController = GradleOpenTelemetry.callWithSpan("CreateGradleModelController", __ ->
      new GradleModelControllerImpl(buildController)
    );
    GradleDslBaseScriptModel model = GradleOpenTelemetry.callWithSpan("GetBaseScriptModelState", ___ ->
      modelController.fetchModelOrNull(GradleDslBaseScriptModel.class)
    );
    buildController.send(GradleDslBaseScriptModelHolder.wrap(model));
  }

  private static @NotNull GradleDaemonModelHolder initAction(
    @NotNull BuildController buildController,
    @NotNull ExecutorService converterExecutor,
    @NotNull GradleVersion gradleVersion
  ) {
    GradleModelControllerImpl modelController = GradleOpenTelemetry.callWithSpan("CreateGradleModelController", __ ->
      new GradleModelControllerImpl(buildController)
    );
    GradleBuild mainGradleBuild = GradleOpenTelemetry.callWithSpan("GetMainGradleBuild", __ ->
      modelController.fetchModel(GradleBuild.class)
    );
    Collection<? extends GradleBuild> nestedGradleBuilds = GradleOpenTelemetry.callWithSpan("GetNestedGradleBuilds", __ ->
      getNestedBuilds(mainGradleBuild, gradleVersion)
    );
    ClassLoader daemonClassLoader = GradleOpenTelemetry.callWithSpan("GetDaemonClassLoader", __ ->
      GradleClassLoaderUtil.getDaemonClassLoader(modelController)
    );
    ToolingSerializerConverter serializer = GradleOpenTelemetry.callWithSpan("GetToolingModelConverter", __ ->
      new ToolingSerializerConverter(daemonClassLoader)
    );
    return GradleOpenTelemetry.callWithSpan("InitModelConsumer", __ ->
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
    @NotNull BuildController buildController,
    @NotNull ExecutorService converterExecutor,
    @NotNull GradleDaemonModelHolder models
  ) {
    BuildController childBuildController = new DefaultBuildController(buildController, models.getRootGradleBuild(), getGradleVersion());
    GradleModelController childModelController = new GradleModelControllerImpl(buildController);
    GradleModelConsumer modelConsumer = models.createModelConsumer(converterExecutor);

    try {
      getModelFetchPhases().forEach(phase -> {
        GradleOpenTelemetry.runWithSpan(phase.getName() + "-gradle", __ -> {
          Set<ProjectImportModelProvider> modelProviders = myModelProviders.getOrDefault(phase, Collections.emptySet());
          populateModels(childBuildController, childModelController, modelProviders, models.getGradleBuilds(), modelConsumer);
          sendPendingState(childBuildController, models, phase);
        });
      });
    }
    catch (Exception e) {
      throw new ExternalSystemException(e);
    }
  }

  private static void populateModels(
    @NotNull BuildController buildController,
    @NotNull GradleModelController modelController,
    @NotNull Collection<ProjectImportModelProvider> modelProviders,
    @NotNull Collection<? extends GradleBuild> gradleBuilds,
    @NotNull GradleModelConsumer modelConsumer
  ) {
    for (ProjectImportModelProvider modelProvider : modelProviders) {
      GradleOpenTelemetry.runWithSpan(modelProvider.getName(), __ -> {
        modelProvider.populateModels(buildController, gradleBuilds, modelConsumer);
        modelProvider.populateModels(modelController, gradleBuilds, modelConsumer);
      });
    }
  }

  private void sendPendingState(
    @NotNull BuildController buildController,
    @NotNull GradleDaemonModelHolder models,
    @NotNull GradleModelFetchPhase phase
  ) {
    GradleOpenTelemetry.runWithSpan("SendPendingState", span -> {
      span.setAttribute("use-streamed-values", myUseStreamedValues);
      if (myUseStreamedValues) {
        GradleModelHolderState state = models.pollPendingState();
        GradleModelHolderState phasedState = state.withPhase(phase);
        buildController.send(phasedState);
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
}
