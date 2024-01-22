// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction;
import com.intellij.gradle.toolingExtension.impl.tooling.telemetry.GradleOpenTelemetry;
import com.intellij.gradle.toolingExtension.impl.tooling.telemetry.GradleTracingContext;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ReflectionUtilRt;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.TargetTypeProvider;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.GradleEnvironment;
import org.gradle.tooling.model.build.JavaEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.serialization.ModelConverter;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalJavaEnvironment;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.build.InternalBuildEnvironment;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author Vladislav.Soroka
 */
public class ProjectImportAction implements BuildAction<ProjectImportAction.AllModels>, Serializable {

  private final Set<ProjectImportModelProvider> myModelProviders = new LinkedHashSet<>();
  private final Set<Class<?>> myTargetTypes = new LinkedHashSet<>();

  private final boolean myIsPreviewMode;
  private boolean myUseProjectsLoadedPhase;
  private @Nullable GradleTracingContext myTracingContext = null;

  private transient @Nullable AllModels myAllModels = null;
  private transient @Nullable GradleBuild myGradleBuild = null;
  private transient @Nullable ModelConverter myModelConverter = null;
  private transient @Nullable GradleOpenTelemetry myTelemetry = null;

  public ProjectImportAction(boolean isPreviewMode) {
    myIsPreviewMode = isPreviewMode;
  }

  public void addProjectImportModelProviders(
    @NotNull Collection<? extends ProjectImportModelProvider> providers
  ) {
    myModelProviders.addAll(providers);
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
    ExecutorService converterExecutor = Executors.newSingleThreadExecutor(new SimpleThreadFactory());
    try {
      configureAdditionalTypes(controller);
      boolean isProjectsLoadedAction = myAllModels == null && myUseProjectsLoadedPhase;
      if (isProjectsLoadedAction || !myUseProjectsLoadedPhase) {
        if (isTracingEnabled()) {
          getTelemetry().start(myTracingContext);
        }
        getTelemetry().runWithSpan("onExecuteStart", __ -> onExecuteStart(controller));
      }
      AllModels allModels = getTelemetry().callWithSpan("doExecute",
                                                        span -> doExecute(controller, converterExecutor, isProjectsLoadedAction));
      if (!isProjectsLoadedAction && allModels != null) {
        onExecuteEnd(allModels);
      }
      return allModels;
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

  /**
   * On first execution of the {@link #doExecute(BuildController, ExecutorService, boolean)}.
   * This method will be called only once, before the first invocation of the method.
   */
  private void onExecuteStart(@NotNull BuildController controller) {
    long startTime = System.currentTimeMillis();
    myGradleBuild = getTelemetry().callWithSpan("GetBuildModel", __ -> controller.getBuildModel());
    AllModels allModels = new AllModels(myGradleBuild);
    allModels.logPerformance("Get model GradleBuild", System.currentTimeMillis() - startTime);
    long startTimeBuildEnv = System.currentTimeMillis();
    BuildEnvironment buildEnvironment = getTelemetry().callWithSpan("GetBuildEnvironment",
                                                                    __ -> controller.findModel(BuildEnvironment.class));
    allModels.setBuildEnvironment(convert(buildEnvironment));
    allModels.logPerformance("Get model BuildEnvironment", System.currentTimeMillis() - startTimeBuildEnv);
    myAllModels = allModels;
    myModelConverter = getToolingModelConverter(controller);
  }

  /**
   * After last execution of the {@link #doExecute(BuildController, ExecutorService, boolean)}.
   * This method will be called only once, after the last invocation of the method.
   */
  private void onExecuteEnd(@NotNull AllModels allModels) {
    byte[] trace = getTelemetry().shutdown();
    allModels.setOpenTelemetryTrace(trace);
  }

  private @Nullable AllModels doExecute(
    @NotNull BuildController controller,
    @NotNull ExecutorService converterExecutor,
    boolean isProjectsLoadedAction
  ) {
    assert myAllModels != null;
    assert myGradleBuild != null;
    assert myModelConverter != null;

    Set<ProjectImportModelProvider> modelProviders = getModelProviders(isProjectsLoadedAction);
    GradleModelFetchAction modelFetchAction = new GradleModelFetchAction(
      myAllModels,
      modelProviders,
      myModelConverter,
      converterExecutor,
      getTelemetry(),
      myIsPreviewMode,
      isProjectsLoadedAction
    );
    long startTime = System.currentTimeMillis();
    modelFetchAction.execute(new DefaultBuildController(controller, myGradleBuild));
    myAllModels.logPerformance("Execute GradleModelFetchAction", System.currentTimeMillis() - startTime);

    return isProjectsLoadedAction && !myAllModels.hasModels() ? null : myAllModels;
  }

  @Contract("null -> null")
  private static BuildEnvironment convert(final @Nullable BuildEnvironment buildEnvironment) {
    if (buildEnvironment == null || buildEnvironment instanceof InternalBuildEnvironment) {
      return buildEnvironment;
    }
    return new InternalBuildEnvironment(
      () -> {
        BuildIdentifier buildIdentifier = buildEnvironment.getBuildIdentifier();
        return new InternalBuildIdentifier(buildIdentifier.getRootDir());
      },
      () -> {
        GradleEnvironment gradle = buildEnvironment.getGradle();
        return gradle.getGradleUserHome();
      },
      () -> {
        GradleEnvironment gradle = buildEnvironment.getGradle();
        return gradle.getGradleVersion();
      },
      () -> {
        JavaEnvironment java = buildEnvironment.getJava();
        return new InternalJavaEnvironment(java.getJavaHome(), java.getJvmArguments());
      }
    );
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

  private boolean isTracingEnabled() {
    return myTracingContext != null;
  }

  private @NotNull GradleOpenTelemetry getTelemetry() {
    if (myTelemetry == null) {
      myTelemetry = new GradleOpenTelemetry();
    }
    return myTelemetry;
  }

  // Note: This class is NOT thread safe, and it is supposed to be used from a single thread.
  //       Performance logging related methods are thread safe.
  public static final class AllModels extends ModelsHolder<BuildModel, ProjectModel> {
    @NotNull private final List<Build> includedBuilds = new ArrayList<>();
    private final Map<String, Long> performanceTrace = new ConcurrentHashMap<>();
    private transient Map<String, String> myBuildsKeyPrefixesMapping;
    private byte[] openTelemetryTrace = ArrayUtilRt.EMPTY_BYTE_ARRAY;

    public AllModels(@NotNull GradleBuild mainBuild) {
      super(DefaultBuild.convertGradleBuild(mainBuild));
    }

    public AllModels(@NotNull IdeaProject ideaProject) {
      super(DefaultBuild.convertIdeaProject(ideaProject));
      addModel(ideaProject, IdeaProject.class);
    }

    @NotNull
    public Build getMainBuild() {
      return (Build)getRootModel();
    }

    @NotNull
    public List<Build> getIncludedBuilds() {
      return includedBuilds;
    }

    @ApiStatus.Internal
    public void addIncludedBuild(@NotNull Build includedBuild) {
      includedBuilds.add(includedBuild);
    }

    @NotNull
    public List<Build> getAllBuilds() {
      List<Build> result = new ArrayList<>();
      result.add(getMainBuild());
      result.addAll(includedBuilds);
      return result;
    }

    @Nullable
    public BuildEnvironment getBuildEnvironment() {
      return getModel(BuildEnvironment.class);
    }

    public void setBuildEnvironment(@Nullable BuildEnvironment buildEnvironment) {
      if (buildEnvironment != null) {
        addModel(buildEnvironment, BuildEnvironment.class);
      }
    }

    public void logPerformance(@NotNull final String description, long millis) {
      performanceTrace.put(description, millis);
    }

    public Map<String, Long> getPerformanceTrace() {
      return performanceTrace;
    }

    public byte[] getOpenTelemetryTrace() {
      return openTelemetryTrace;
    }

    public void setOpenTelemetryTrace(byte[] openTelemetryTrace) {
      this.openTelemetryTrace = openTelemetryTrace;
    }

    @Override
    public void applyPathsConverter(@NotNull Consumer<Object> pathsConverter) {
      super.applyPathsConverter(pathsConverter);
      BuildEnvironment buildEnvironment = getBuildEnvironment();
      if (buildEnvironment != null) {
        pathsConverter.accept(buildEnvironment);
      }
      myBuildsKeyPrefixesMapping = new HashMap<>();
      convertPaths(pathsConverter, getMainBuild());
      for (Build includedBuild : includedBuilds) {
        convertPaths(pathsConverter, includedBuild);
      }
    }

    private void convertPaths(@NotNull Consumer<Object> fileMapper, @NotNull Build build) {
      String originalKey = getBuildKeyPrefix(build.getBuildIdentifier());
      fileMapper.accept(build);
      String currentKey = getBuildKeyPrefix(build.getBuildIdentifier());
      if (!originalKey.equals(currentKey)) {
        myBuildsKeyPrefixesMapping.put(currentKey, originalKey);
      }
    }

    @NotNull
    @Override
    protected String getBuildKeyPrefix(@NotNull BuildIdentifier buildIdentifier) {
      String currentKey = super.getBuildKeyPrefix(buildIdentifier);
      String originalKey = myBuildsKeyPrefixesMapping == null ? null : myBuildsKeyPrefixesMapping.get(currentKey);
      return originalKey == null ? currentKey : originalKey;
    }
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
