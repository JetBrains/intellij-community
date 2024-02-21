// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry;
import com.intellij.gradle.toolingExtension.impl.telemetry.GradleTracingContext;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.util.ReflectionUtilRt;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.TargetTypeProvider;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import com.intellij.gradle.toolingExtension.impl.model.utilTurnOffDefaultTasksModel.TurnOffDefaultTasks;
import com.intellij.gradle.toolingExtension.impl.modelSerialization.ModelConverter;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Vladislav.Soroka
 */
public class GradleModelFetchAction implements BuildAction<AllModels>, Serializable {

  private final Set<ProjectImportModelProvider> myModelProviders = new LinkedHashSet<>();
  private final Set<Class<?>> myTargetTypes = new LinkedHashSet<>();

  private boolean myUseProjectsLoadedPhase;
  private @Nullable GradleTracingContext myTracingContext = null;

  private transient @Nullable AllModels myAllModels = null;
  private transient @Nullable GradleBuild myMainGradleBuild = null;
  private transient @Nullable Collection<? extends GradleBuild> myNestedGradleBuilds = null;
  private transient @Nullable ModelConverter myModelConverter = null;
  private transient @Nullable GradleOpenTelemetry myTelemetry = null;

  public GradleModelFetchAction addProjectImportModelProviders(
    @NotNull Collection<? extends ProjectImportModelProvider> providers
  ) {
    myModelProviders.addAll(providers);
    return this;
  }

  @ApiStatus.Internal
  public Set<Class<?>> getModelProvidersClasses() {
    Set<Class<?>> result = new LinkedHashSet<>();
    for (ProjectImportModelProvider provider : myModelProviders) {
      result.add(provider.getClass());
    }
    return result;
  }

  public void addTargetTypes(@NotNull Set<Class<?>> targetTypes) {
    myTargetTypes.addAll(targetTypes);
  }

  public void prepareForPhasedExecuter() {
    myUseProjectsLoadedPhase = true;
  }

  public void prepareForNonPhasedExecuter() {
    myUseProjectsLoadedPhase = false;
  }

  public void setTracingContext(@NotNull GradleTracingContext tracingContext) {
    myTracingContext = tracingContext;
  }

  @NotNull
  protected ModelConverter getToolingModelConverter(@NotNull BuildController controller) {
    return ModelConverter.NOP;
  }

  @Override
  public @Nullable AllModels execute(@NotNull BuildController controller) {
    configureAdditionalTypes(controller);
    return withConverterExecutor(converterExecutor -> {
      return withOpenTelemetry(telemetry -> {
        return telemetry.callWithSpan("ProjectImportAction", __ -> {
          return doExecute(controller, converterExecutor, telemetry);
        });
      });
    });
  }

  private static @Nullable AllModels withConverterExecutor(@NotNull Function<ExecutorService, AllModels> action) {
    ExecutorService converterExecutor = Executors.newSingleThreadExecutor(new SimpleThreadFactory());
    try {
      return action.apply(converterExecutor);
    }
    finally {
      converterExecutor.shutdown();
      try {
        converterExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private <T> T withOpenTelemetry(@NotNull Function<GradleOpenTelemetry, T> action) {
    boolean isProjectsLoadedAction = myAllModels == null && myUseProjectsLoadedPhase;
    if (isProjectsLoadedAction || !myUseProjectsLoadedPhase) {
      myTelemetry = new GradleOpenTelemetry();
      if (myTracingContext != null) {
        myTelemetry.start(myTracingContext);
      }
    }

    assert myTelemetry != null;

    try {
      return action.apply(myTelemetry);
    }
    finally {
      if (!isProjectsLoadedAction && myAllModels != null) {
        byte[] trace = myTelemetry.shutdown();
        myAllModels.setOpenTelemetryTrace(trace);
      }
    }
  }

  private @Nullable AllModels doExecute(
    @NotNull BuildController controller,
    @NotNull ExecutorService converterExecutor,
    @NotNull GradleOpenTelemetry telemetry
  ) {
    boolean isProjectsLoadedAction = myAllModels == null && myUseProjectsLoadedPhase;

    if (isProjectsLoadedAction || !myUseProjectsLoadedPhase) {
      initAction(controller, telemetry);
    }

    executeAction(controller, converterExecutor, telemetry, isProjectsLoadedAction);

    if (isProjectsLoadedAction) {
      telemetry.runWithSpan("TurnOffDefaultTasks", __ ->
        controller.getModel(TurnOffDefaultTasks.class)
      );
    }

    assert myAllModels != null;
    return isProjectsLoadedAction && !myAllModels.hasModels() ? null : myAllModels;
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

  private void initAction(
    @NotNull BuildController controller,
    @NotNull GradleOpenTelemetry telemetry
  ) {
    GradleBuild mainGradleBuild = telemetry.callWithSpan("GetMainGradleBuild", __ ->
      controller.getBuildModel()
    );
    Collection<? extends GradleBuild> nestedGradleBuilds = telemetry.callWithSpan("GetNestedGradleBuilds", __ ->
      getNestedBuilds(controller, mainGradleBuild)
    );
    BuildEnvironment buildEnvironment = telemetry.callWithSpan("GetBuildEnvironment", __ ->
      controller.getModel(BuildEnvironment.class)
    );
    ModelConverter modelConverter = telemetry.callWithSpan("GetToolingModelConverter", __ ->
      getToolingModelConverter(controller)
    );
    AllModels allModels = telemetry.callWithSpan("InitAllModels", __ ->
      new AllModels(mainGradleBuild, nestedGradleBuilds, buildEnvironment)
    );

    myMainGradleBuild = mainGradleBuild;
    myNestedGradleBuilds = nestedGradleBuilds;
    myModelConverter = modelConverter;
    myAllModels = allModels;
  }

  private static Collection<? extends GradleBuild> getNestedBuilds(@NotNull BuildController controller, @NotNull GradleBuild build) {
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

  private void executeAction(
    @NotNull BuildController controller,
    @NotNull ExecutorService converterExecutor,
    @NotNull GradleOpenTelemetry telemetry,
    boolean isProjectsLoadedAction
  ) {
    assert myAllModels != null;
    assert myMainGradleBuild != null;
    assert myNestedGradleBuilds != null;
    assert myModelConverter != null;

    Set<ProjectImportModelProvider> modelProviders = getModelProviders(isProjectsLoadedAction);
    GradleCustomModelFetchAction buildAction = new GradleCustomModelFetchAction(
      myAllModels,
      modelProviders,
      myModelConverter,
      converterExecutor,
      telemetry
    );
    buildAction.execute(
      new DefaultBuildController(controller, myMainGradleBuild),
      myMainGradleBuild,
      myNestedGradleBuilds
    );
  }

  private Set<ProjectImportModelProvider> getModelProviders(boolean isProjectsLoadedAction) {
    if (myUseProjectsLoadedPhase) {
      if (isProjectsLoadedAction) {
        return myModelProviders.stream()
          .filter(it -> it.getPhase().equals(GradleModelFetchPhase.PROJECT_LOADED_PHASE))
          .collect(Collectors.toSet());
      }
      return myModelProviders.stream()
        .filter(it -> !it.getPhase().equals(GradleModelFetchPhase.PROJECT_LOADED_PHASE))
        .collect(Collectors.toSet());
    }
    return myModelProviders;
  }

  // Use this static class as a simple ThreadFactory to prevent a memory leak when passing an anonymous ThreadFactory object to
  // Executors.newSingleThreadExecutor. Memory leak will occur on the Gradle Daemon otherwise.
  private static final class SimpleThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(@NotNull Runnable runnable) {
      return new Thread(runnable, "idea-tooling-model-converter");
    }
  }
}
