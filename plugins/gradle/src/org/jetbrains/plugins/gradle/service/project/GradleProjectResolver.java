// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.build.events.MessageEvent;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.gradle.toolingExtension.impl.telemetry.GradleTracingContext;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.diagnostic.ExternalSystemSyncDiagnostic;
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.project.PerformanceTrace;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemSyncActionsCollector;
import com.intellij.openapi.externalSystem.statistics.Phase;
import com.intellij.openapi.externalSystem.util.ExternalSystemDebugEnvironment;
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.tooling.BuildActionFailureException;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.issue.DeprecatedGradleVersionIssue;
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.model.data.CompositeBuildData;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.remote.impl.GradleLibraryNamesMixer;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.execution.GradleInitScriptUtil;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleBuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.telemetry.GradleDaemonOpenTelemetryUtil;
import org.jetbrains.plugins.gradle.util.telemetry.GradleOpenTelemetryTraceExporter;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static org.jetbrains.plugins.gradle.service.project.ArtifactMappingServiceKt.OWNER_BASE_GRADLE;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleId;

/**
 * @author Vladislav Soroka
 */
public class GradleProjectResolver implements ExternalSystemProjectResolver<GradleExecutionSettings> {

  private static final Logger LOG = Logger.getInstance(GradleProjectResolver.class);
  private static final ExternalSystemSyncDiagnostic syncMetrics = ExternalSystemSyncDiagnostic.getInstance();

  @NotNull private final GradleExecutionHelper myHelper;
  private final GradleLibraryNamesMixer myLibraryNamesMixer = new GradleLibraryNamesMixer();

  private final MultiMap<ExternalSystemTaskId, CancellationTokenSource> myCancellationMap = MultiMap.createConcurrent();
  public static final Key<Map<String/* module id */, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>>> RESOLVED_SOURCE_SETS =
    Key.create("resolvedSourceSets");
  public static final Key<Map<String/* output path */, Pair<String /* module id*/, ExternalSystemSourceType>>> MODULES_OUTPUTS =
    Key.create("moduleOutputsMap");
  public static final Key<MultiMap<ExternalSystemSourceType, String /* output path*/>> GRADLE_OUTPUTS = Key.create("gradleOutputs");

  private static final Key<File> GRADLE_HOME_DIR = Key.create("gradleHomeDir");

  // This constructor is called by external system API, see AbstractExternalSystemFacadeImpl class constructor.
  @SuppressWarnings("UnusedDeclaration")
  public GradleProjectResolver() {
    this(new GradleExecutionHelper());
  }

  public GradleProjectResolver(@NotNull GradleExecutionHelper helper) {
    myHelper = helper;
  }

  @Nullable
  @Override
  public DataNode<ProjectData> resolveProjectInfo(@NotNull final ExternalSystemTaskId syncTaskId,
                                                  @NotNull final String projectPath,
                                                  final boolean isPreviewMode,
                                                  @Nullable final GradleExecutionSettings settings,
                                                  @Nullable ProjectResolverPolicy resolverPolicy,
                                                  @NotNull final ExternalSystemTaskNotificationListener listener)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException {

    GradlePartialResolverPolicy gradleResolverPolicy = null;
    if (resolverPolicy != null) {
      if (resolverPolicy instanceof GradlePartialResolverPolicy) {
        gradleResolverPolicy = (GradlePartialResolverPolicy)resolverPolicy;
      }
      else {
        throw new ExternalSystemException("Unsupported project resolver policy: " + resolverPolicy.getClass().getName());
      }
    }

    // Create project preview model w/o request to gradle, there are two main reasons for the it:
    // * Slow project open - even the simplest project info provided by gradle can be gathered too long (mostly because of new gradle distribution download and downloading build script dependencies)
    // * Ability to open  an invalid projects (e.g. with errors in build scripts)
    if (isPreviewMode) {
      return GradlePreviewCustomizer.Companion.getCustomizer(projectPath, syncTaskId).resolvePreviewProjectInfo(projectPath, syncTaskId, settings);
    }

    DefaultProjectResolverContext resolverContext =
      new DefaultProjectResolverContext(syncTaskId, projectPath, settings, listener, gradleResolverPolicy, false);
    final CancellationTokenSource cancellationTokenSource = resolverContext.getCancellationTokenSource();
    myCancellationMap.putValue(resolverContext.getExternalSystemTaskId(), cancellationTokenSource);

    final long activityId = resolverContext.getExternalSystemTaskId().getId();
    ExternalSystemSyncActionsCollector.logSyncStarted(resolverContext.getExternalSystemTaskId().findProject(), activityId);
    syncMetrics.getOrStartSpan(ExternalSystemSyncDiagnostic.gradleSyncSpanName);

    Span gradleExecutionSpan = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("GradleExecution")
      .startSpan();
    try (Scope ignore = gradleExecutionSpan.makeCurrent()) {
      if (settings != null) {
        myHelper.ensureInstalledWrapper(syncTaskId, projectPath, settings, listener, cancellationTokenSource.token());
      }

      Predicate<GradleProjectResolverExtension> extensionsFilter =
        gradleResolverPolicy != null ? gradleResolverPolicy.getExtensionsFilter() : null;
      final GradleProjectResolverExtension projectResolverChain = createProjectResolverChain(resolverContext, extensionsFilter);
      final DataNode<ProjectData> projectDataNode = myHelper.execute(
        projectPath, settings, syncTaskId, listener, cancellationTokenSource,
        getProjectDataFunction(resolverContext, projectResolverChain, false));

      // auto-discover buildSrc projects of the main and included builds
      if (GradleVersionUtil.isGradleOlderThan(resolverContext.getProjectGradleVersion(), "8.0")) {
        File gradleUserHome = resolverContext.getUserData(GRADLE_HOME_DIR);
        new GradleBuildSrcProjectsResolver(this, resolverContext, gradleUserHome, settings, listener, syncTaskId, projectResolverChain)
          .discoverAndAppendTo(projectDataNode);
      }

      return projectDataNode;
    }
    finally {
      myCancellationMap.remove(resolverContext.getExternalSystemTaskId(), cancellationTokenSource);
      gradleExecutionSpan.end();
    }
  }

  @NotNull
  Function<ProjectConnection, DataNode<ProjectData>> getProjectDataFunction(DefaultProjectResolverContext resolverContext,
                                                                            GradleProjectResolverExtension projectResolverChain,
                                                                            boolean isBuildSrcProject) {
    return new ProjectConnectionDataNodeFunction(resolverContext, projectResolverChain, isBuildSrcProject);
  }

  @NotNull
  GradleExecutionHelper getHelper() {
    return myHelper;
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener) {
    for (CancellationTokenSource cancellationTokenSource : myCancellationMap.get(id)) {
      cancellationTokenSource.cancel();
    }
    return true;
  }

  @NotNull
  private DataNode<ProjectData> doResolveProjectInfo(@NotNull final DefaultProjectResolverContext resolverCtx,
                                                     @NotNull final GradleProjectResolverExtension projectResolverChain,
                                                     boolean isBuildSrcProject)
    throws IllegalArgumentException, IllegalStateException {
    final long activityId = resolverCtx.getExternalSystemTaskId().getId();
    final PerformanceTrace performanceTrace = new PerformanceTrace(activityId);
    final GradleProjectResolverExtension tracedResolverChain = new TracedProjectResolverExtension(projectResolverChain, performanceTrace);

    final BuildEnvironment buildEnvironment = GradleExecutionHelper.getBuildEnvironment(resolverCtx);
    if (buildEnvironment != null) {
      resolverCtx.setBuildEnvironment(buildEnvironment);
    }
    final GradleVersion gradleVersion = ObjectUtils.doIfNotNull(buildEnvironment, it ->
      GradleVersion.version(it.getGradle().getGradleVersion())
    );

    boolean useCustomSerialization = Registry.is("gradle.tooling.custom.serializer", true);
    final ProjectImportAction projectImportAction =
      useCustomSerialization
      ? new ProjectImportActionWithCustomSerializer(resolverCtx.isPreviewMode())
      : new ProjectImportAction(resolverCtx.isPreviewMode());

    GradleExecutionSettings executionSettings = resolverCtx.getSettings();
    if (executionSettings == null) {
      executionSettings = new GradleExecutionSettings(null, null, DistributionType.BUNDLED, false);
    }

    configureExecutionArgumentsAndVmOptions(executionSettings, resolverCtx, gradleVersion, isBuildSrcProject);
    final Set<Class<?>> toolingExtensionClasses = new HashSet<>();
    for (GradleProjectResolverExtension resolverExtension = tracedResolverChain;
         resolverExtension != null;
         resolverExtension = resolverExtension.getNext()) {
      // inject ProjectResolverContext into gradle project resolver extensions
      resolverExtension.setProjectResolverContext(resolverCtx);
      // pre-import checks
      resolverExtension.preImportCheck();

      projectImportAction.addTargetTypes(resolverExtension.getTargetTypes());

      // register classes of extra gradle project models required for extensions (e.g. com.android.builder.model.AndroidProject)
      try {
        projectImportAction.addProjectImportModelProviders(resolverExtension.getModelProviders());
      }
      catch (Throwable t) {
        LOG.warn(t);
      }

      // collect tooling extensions classes
      try {
        toolingExtensionClasses.addAll(resolverExtension.getToolingExtensionsClasses());
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    }

    GradleExecutionHelper.attachTargetPathMapperInitScript(executionSettings);
    var initScript = GradleInitScriptUtil.createMainInitScript(isBuildSrcProject, toolingExtensionClasses);
    executionSettings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.toString());

    if (!executionSettings.isDownloadSources()) {
      GradleExecutionHelper.attachIdeaPluginConfigurator(executionSettings);
    }

    BuildActionRunner buildActionRunner = new BuildActionRunner(resolverCtx, projectImportAction, executionSettings, myHelper);
    resolverCtx.checkCancelled();

    final long startTime = System.currentTimeMillis();

    syncMetrics.getOrStartSpan(Phase.GRADLE_CALL.name(), ExternalSystemSyncDiagnostic.gradleSyncSpanName);
    ExternalSystemSyncActionsCollector.logPhaseStarted(null, activityId, Phase.GRADLE_CALL);

    ProjectImportAction.AllModels allModels;
    int errorsCount = 0;
    CountDownLatch buildFinishWaiter = new CountDownLatch(1);

    Span gradleCallSpan = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("GradleCall")
      .startSpan();
    if (GradleDaemonOpenTelemetryUtil.isDaemonTracingEnabled()) {
      GradleTracingContext gradleDaemonObservabilityContext = getActionTelemetryContext(gradleCallSpan);
      projectImportAction.setTracingContext(gradleDaemonObservabilityContext);
    }
    try (Scope ignore = gradleCallSpan.makeCurrent()) {
      allModels = buildActionRunner.fetchModels(
        models -> {
          for (GradleProjectResolverExtension resolver = tracedResolverChain; resolver != null; resolver = resolver.getNext()) {
            resolver.projectsLoaded(models);
          }
        },
        (exception) -> {
          try {
            for (GradleProjectResolverExtension resolver = tracedResolverChain; resolver != null; resolver = resolver.getNext()) {
              resolver.buildFinished(exception);
            }
          }
          finally {
            buildFinishWaiter.countDown();
          }
        });
      if (gradleVersion != null && GradleJvmSupportMatrix.isGradleDeprecatedByIdea(gradleVersion)) {
        resolverCtx.report(MessageEvent.Kind.WARNING, new DeprecatedGradleVersionIssue(gradleVersion, resolverCtx.getProjectPath()));
      }
      performanceTrace.addTrace(allModels.getPerformanceTrace());
    }
    catch (Throwable t) {
      buildFinishWaiter.countDown();
      errorsCount += 1;
      gradleCallSpan.setAttribute("error.count", errorsCount);
      syncMetrics.getOrStartSpan(Phase.GRADLE_CALL.name()).setAttribute("error.count", errorsCount);
      throw t;
    }
    finally {
      ProgressIndicatorUtils.awaitWithCheckCanceled(buildFinishWaiter);
      final long timeInMs = (System.currentTimeMillis() - startTime);
      performanceTrace.logPerformance("Gradle data obtained", timeInMs);
      syncMetrics.getOrStartSpan(Phase.GRADLE_CALL.name()).end();
      ExternalSystemSyncActionsCollector.logPhaseFinished(null, activityId, Phase.GRADLE_CALL, timeInMs, errorsCount);
      LOG.debug(String.format("Gradle data obtained in %d ms", timeInMs));
      gradleCallSpan.end();
    }

    exportRecordedTraces(allModels);

    resolverCtx.checkCancelled();
    if (useCustomSerialization) {
      allModels.initToolingSerializer();
    }

    allModels.setBuildEnvironment(buildEnvironment);
    final long startDataConversionTime = System.currentTimeMillis();
    int resolversErrorsCount = 0;

    Span gradleProjectResolversSpan = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("GradleProjectResolvers")
      .startSpan();
    try (GradleTargetPathsConverter pathsConverter = new GradleTargetPathsConverter(executionSettings);
         Scope ignore = gradleProjectResolversSpan.makeCurrent()) {
      pathsConverter.mayBeApplyTo(allModels);
      return convertData(allModels, executionSettings, resolverCtx, gradleVersion,
                         tracedResolverChain, performanceTrace, isBuildSrcProject, useCustomSerialization);
    }
    catch (Throwable t) {
      resolversErrorsCount += 1;
      throw t;
    }
    finally {
      final long timeConversionInMs = (System.currentTimeMillis() - startDataConversionTime);
      performanceTrace.logPerformance("Gradle project data processed", timeConversionInMs);
      LOG.debug(String.format("Project data resolved in %d ms", timeConversionInMs));
      gradleProjectResolversSpan.end();
      syncMetrics.getOrStartSpan(Phase.PROJECT_RESOLVERS.name()).end();
      ExternalSystemSyncActionsCollector.logPhaseFinished(null, activityId, Phase.PROJECT_RESOLVERS, timeConversionInMs,
                                                          resolversErrorsCount);
    }
  }

  @NotNull
  private DataNode<ProjectData> convertData(@NotNull ProjectImportAction.AllModels allModels,
                                            @NotNull GradleExecutionSettings executionSettings,
                                            @NotNull DefaultProjectResolverContext resolverCtx,
                                            @Nullable GradleVersion gradleVersion,
                                            @NotNull GradleProjectResolverExtension tracedResolverChain,
                                            @NotNull PerformanceTrace performanceTrace,
                                            boolean isBuildSrcProject,
                                            boolean useCustomSerialization) {
    final long activityId = resolverCtx.getExternalSystemTaskId().getId();

    syncMetrics.getOrStartSpan(Phase.PROJECT_RESOLVERS.name(), ExternalSystemSyncDiagnostic.gradleSyncSpanName);
    ExternalSystemSyncActionsCollector.logPhaseStarted(null, activityId, Phase.PROJECT_RESOLVERS);
    extractExternalProjectModels(allModels, resolverCtx, useCustomSerialization);

    String projectName = allModels.getMainBuild().getName();
    ModifiableGradleProjectModelImpl modifiableGradleProjectModel =
      new ModifiableGradleProjectModelImpl(projectName, resolverCtx.getProjectPath());
    ToolingModelsProvider modelsProvider = new ToolingModelsProviderImpl(allModels);
    ProjectModelContributor.EP_NAME.forEachExtensionSafe(extension -> {
      resolverCtx.checkCancelled();
      final long starResolveTime = System.currentTimeMillis();
      String modelContributorName = extension.getClass().getSimpleName();
      ExternalSystemTelemetryUtil.runWithSpan(GradleConstants.SYSTEM_ID, "ExternalSystemProjectModelContributor",
                                              (span) -> {
                                                span.setAttribute("contributor.name", modelContributorName);
                                                extension.accept(modifiableGradleProjectModel, modelsProvider, resolverCtx);
                                              });
      final long resolveTimeInMs = (System.currentTimeMillis() - starResolveTime);
      performanceTrace.logPerformance("Project model contributed by " + modelContributorName, resolveTimeInMs);
      LOG.debug(String.format("Project model contributed by `" + modelContributorName + "` in %d ms", resolveTimeInMs));
    });

    DataNode<ProjectData> projectDataNode = modifiableGradleProjectModel.buildDataNodeGraph();
    DataNode<PerformanceTrace> performanceTraceNode = new DataNode<>(PerformanceTrace.TRACE_NODE_KEY, performanceTrace, projectDataNode);
    projectDataNode.addChild(performanceTraceNode);

    Set<? extends IdeaModule> gradleModules = Collections.emptySet();
    IdeaProject ideaProject = allModels.getModel(IdeaProject.class);
    if (ideaProject != null) {
      tracedResolverChain.populateProjectExtraModels(ideaProject, projectDataNode);
      gradleModules = ideaProject.getModules();
      if (gradleModules == null || gradleModules.isEmpty()) {
        throw new IllegalStateException("No modules found for the target project: " + ideaProject);
      }
    }

    Collection<IdeaModule> includedModules = exposeCompositeBuild(allModels, resolverCtx, projectDataNode);
    final Map<String /* module id */, Pair<DataNode<ModuleData>, IdeaModule>> moduleMap = new HashMap<>();
    final Map<String /* module id */, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetsMap = new HashMap<>();
    projectDataNode.putUserData(RESOLVED_SOURCE_SETS, sourceSetsMap);

    final Map<String/* output path */, Pair<String /* module id*/, ExternalSystemSourceType>> moduleOutputsMap =
      CollectionFactory.createFilePathMap();
    projectDataNode.putUserData(MODULES_OUTPUTS, moduleOutputsMap);

    final ArtifactMappingService artifactsMap = resolverCtx.getArtifactsMap();

    // import modules data
    for (IdeaModule gradleModule : ContainerUtil.concat(gradleModules, includedModules)) {
      if (gradleModule == null) {
        continue;
      }

      resolverCtx.checkCancelled();

      if (ExternalSystemDebugEnvironment.DEBUG_ORPHAN_MODULES_PROCESSING) {
        LOG.info(String.format("Importing module data: %s", gradleModule));
      }
      final String moduleName = gradleModule.getName();
      if (moduleName == null) {
        throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
      }

      DataNode<ModuleData> moduleDataNode = tracedResolverChain.createModule(gradleModule, projectDataNode);
      if (moduleDataNode == null) continue;
      String mainModuleId = getModuleId(resolverCtx, gradleModule);

      if (moduleMap.containsKey(mainModuleId)) {
        // we should ensure deduplicated module names in the scope of single import
        throw new IllegalStateException("Attempt to add module with already existing id [" + mainModuleId + "]\n" +
                                        " new module: " + gradleModule + "\n" +
                                        " existing module: " + moduleMap.get(mainModuleId).second);
      }
      moduleMap.put(mainModuleId, Pair.create(moduleDataNode, gradleModule));
    }

    executionSettings.getExecutionWorkspace().setModuleIdIndex(moduleMap);

    File gradleHomeDir = null;
    // populate modules nodes
    for (final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values()) {
      final DataNode<ModuleData> moduleDataNode = pair.first;
      final IdeaModule ideaModule = pair.second;

      if (gradleHomeDir == null) {
        final BuildScriptClasspathModel buildScriptClasspathModel =
          resolverCtx.getExtraProject(ideaModule, BuildScriptClasspathModel.class);
        if (buildScriptClasspathModel != null) {
          gradleHomeDir = buildScriptClasspathModel.getGradleHomeDir();
        }
      }

      tracedResolverChain.populateModuleContentRoots(ideaModule, moduleDataNode);
      tracedResolverChain.populateModuleCompileOutputSettings(ideaModule, moduleDataNode);
      if (!isBuildSrcProject) {
        tracedResolverChain.populateModuleTasks(ideaModule, moduleDataNode, projectDataNode);
      }

      final List<DataNode<? extends ModuleData>> modules = new SmartList<>();
      modules.add(moduleDataNode);
      modules.addAll(findAll(moduleDataNode, GradleSourceSetData.KEY));

      final ExternalSystemSourceType[] sourceTypes = new ExternalSystemSourceType[]{
        ExternalSystemSourceType.SOURCE,
        ExternalSystemSourceType.RESOURCE,
        ExternalSystemSourceType.TEST,
        ExternalSystemSourceType.TEST_RESOURCE
      };
      for (DataNode<? extends ModuleData> module : modules) {
        final ModuleData moduleData = module.getData();
        for (ExternalSystemSourceType sourceType : sourceTypes) {
          final String path = moduleData.getCompileOutputPath(sourceType);
          if (path != null) {
            moduleOutputsMap.put(path, Pair.create(moduleData.getId(), sourceType));
          }
        }

        if (moduleData instanceof GradleSourceSetData) {
          for (File artifactFile : moduleData.getArtifacts()) {
            artifactsMap.storeModuleId(toCanonicalPath(artifactFile.getPath()), moduleData.getId());
          }
        }
      }

      ExternalProject externalProject = resolverCtx.getExtraProject(ideaModule, ExternalProject.class);
      if (externalProject != null) {
        externalProject.getSourceSetModel().getAdditionalArtifacts().forEach((artifactFile) -> {
          String path = toCanonicalPath(artifactFile.getPath());
          ModuleMappingInfo mapping = artifactsMap.getModuleMapping(path);
          if (mapping != null && OWNER_BASE_GRADLE.equals(mapping.getOwnerId())) {
            artifactsMap.markArtifactPath(path, true);
          }
        });
      }
    }
    // reuse same gradle home (for auto-discovered buildSrc projects) also for partial imports which doesn't request BuildScriptClasspathModel
    if (gradleHomeDir == null && executionSettings.getGradleHome() != null) {
      gradleHomeDir = new File(executionSettings.getGradleHome());
    }
    resolverCtx.putUserData(GRADLE_HOME_DIR, gradleHomeDir);

    for (final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values()) {
      final DataNode<ModuleData> moduleDataNode = pair.first;
      final IdeaModule ideaModule = pair.second;
      tracedResolverChain.populateModuleDependencies(ideaModule, moduleDataNode, projectDataNode);
      tracedResolverChain.populateModuleExtraModels(ideaModule, moduleDataNode);
    }
    mergeSourceSetContentRoots(moduleMap, resolverCtx);
    if (resolverCtx.isResolveModulePerSourceSet()) {
      mergeLibraryAndModuleDependencyData(resolverCtx, projectDataNode, resolverCtx.getGradleUserHome(), gradleHomeDir, gradleVersion);
    }

    processBuildSrcModules(resolverCtx, projectDataNode);

    for (GradleProjectResolverExtension resolver = tracedResolverChain; resolver != null; resolver = resolver.getNext()) {
      resolver.resolveFinished(projectDataNode);
    }

    projectDataNode.putUserData(RESOLVED_SOURCE_SETS, null);
    projectDataNode.putUserData(MODULES_OUTPUTS, null);

    // ensure unique library names
    Collection<DataNode<LibraryData>> libraries = getChildren(projectDataNode, ProjectKeys.LIBRARY);
    myLibraryNamesMixer.mixNames(libraries);

    return projectDataNode;
  }

  private static void processBuildSrcModules(DefaultProjectResolverContext ctx, DataNode<ProjectData> projectDataNode) {
    // since Gradle 8.0 buildSrc are available as composite build members
    DataNode<CompositeBuildData> compositeNode = find(projectDataNode, CompositeBuildData.KEY);
    if (compositeNode == null) return;

    GradleBuildSrcProjectsResolver.Index index = GradleBuildSrcProjectsResolver.prepareIndexes(projectDataNode);

    CompositeBuildData compositeBuildData = compositeNode.getData();
    for (BuildParticipant participant : compositeBuildData.getCompositeParticipants()) {
      if (participant.getRootProjectName().endsWith("buildSrc")) {
        Set<String> buildSrcProjectPaths = participant.getProjects();

        @NotNull Collection<DataNode<BuildScriptClasspathData>> buildClasspathNodes =
          index.buildClasspathNodesMap().get(Path.of(participant.getRootPath()).getParent());

        @NotNull Map<String, DataNode<? extends ModuleData>> buildSrcModules = new HashMap<>();
        AtomicReference<DataNode<? extends ModuleData>> buildSrcModuleNode = new AtomicReference<>();

        findAll(projectDataNode, ProjectKeys.MODULE).stream()
          .filter(node -> buildSrcProjectPaths.contains(node.getData().getLinkedExternalProjectPath()))
          .forEach(node -> {
            buildSrcModules.put(node.getData().getId(), node);
            findAll(node, GradleSourceSetData.KEY).forEach(
              sourceSetNode -> buildSrcModules.put(sourceSetNode.getData().getId(), sourceSetNode));

            if (participant.getRootPath().equals(node.getData().getLinkedExternalProjectPath())) {
              if (ctx.isResolveModulePerSourceSet()) {
                buildSrcModuleNode.set(findChild(node, GradleSourceSetData.KEY,
                                                 sourceSetNode -> sourceSetNode.getData().getExternalName().endsWith(":main")));
              }
              else {
                buildSrcModuleNode.set(node);
              }
            }
          });

        GradleBuildSrcProjectsResolver.addBuildSrcToBuildScriptClasspathData(buildClasspathNodes,
                                                                             buildSrcModules,
                                                                             buildSrcModuleNode.get());
      }
    }
  }

  private static void configureExecutionArgumentsAndVmOptions(@NotNull GradleExecutionSettings executionSettings,
                                                              @NotNull DefaultProjectResolverContext resolverCtx,
                                                              @Nullable GradleVersion gradleVersion,
                                                              boolean isBuildSrcProject) {
    executionSettings.withArgument("-Didea.gradle.download.sources=" + executionSettings.isDownloadSources());
    executionSettings.withArgument("-Didea.sync.active=true");
    if (resolverCtx.isResolveModulePerSourceSet()) {
      executionSettings.withArgument("-Didea.resolveSourceSetDependencies=true");
    }
    GradleVersion usedGradleVersion = Objects.requireNonNullElseGet(gradleVersion, GradleVersion::current);
    if (executionSettings.isParallelModelFetch() && GradleVersionUtil.isGradleAtLeast(usedGradleVersion, "7.4")) {
      executionSettings.withArgument("-Didea.parallelModelFetch.enabled=true");
    }
    if (!isBuildSrcProject) {
      for (GradleBuildParticipant buildParticipant : executionSettings.getExecutionWorkspace().getBuildParticipants()) {
        executionSettings.withArguments(GradleConstants.INCLUDE_BUILD_CMD_OPTION, buildParticipant.getProjectPath());
      }
    }

    GradleImportCustomizer importCustomizer = GradleImportCustomizer.get();
    GradleProjectResolverUtil.createProjectResolvers(resolverCtx).forEachOrdered(extension -> {
      if (importCustomizer == null || importCustomizer.useExtraJvmArgs()) {
        // collect extra JVM arguments provided by gradle project resolver extensions
        ParametersList parametersList = new ParametersList();
        for (Pair<String, String> jvmArg : extension.getExtraJvmArgs()) {
          parametersList.addProperty(jvmArg.first, jvmArg.second);
        }
        executionSettings.withVmOptions(parametersList.getParameters());
      }
      // collect extra command-line arguments
      executionSettings.withArguments(extension.getExtraCommandLineArgs());
    });
  }

  @NotNull
  private static Collection<IdeaModule> exposeCompositeBuild(ProjectImportAction.AllModels allModels,
                                                             DefaultProjectResolverContext resolverCtx,
                                                             DataNode<ProjectData> projectDataNode) {
    if (resolverCtx.getSettings() != null && !resolverCtx.getSettings().getExecutionWorkspace().getBuildParticipants().isEmpty()) {
      return Collections.emptyList();
    }
    CompositeBuildData compositeBuildData;
    List<IdeaModule> gradleIncludedModules = new SmartList<>();
    List<Build> includedBuilds = allModels.getIncludedBuilds();
    if (!includedBuilds.isEmpty()) {
      ProjectData projectData = projectDataNode.getData();
      compositeBuildData = new CompositeBuildData(projectData.getLinkedExternalProjectPath());
      for (Build build : includedBuilds) {
        if (!build.getProjects().isEmpty()) {
          IdeaProject ideaProject = allModels.getModel(build, IdeaProject.class);
          if (ideaProject != null) {
            gradleIncludedModules.addAll(ideaProject.getModules());
          }
          String rootProjectName = build.getName();
          BuildParticipant buildParticipant = new BuildParticipant();
          String projectPath = toCanonicalPath(build.getBuildIdentifier().getRootDir().getPath());
          String parentPath = build.getParentBuildIdentifier() != null ?
                              toCanonicalPath(build.getParentBuildIdentifier().getRootDir().getPath()) : null;
          buildParticipant.setRootProjectName(rootProjectName);
          buildParticipant.setRootPath(projectPath);
          buildParticipant.setParentRootPath(parentPath);
          if (ideaProject != null) {
            for (IdeaModule module : ideaProject.getModules()) {
              String modulePath = toCanonicalPath(module.getGradleProject().getProjectDirectory().getPath());
              buildParticipant.getProjects().add(modulePath);
            }
          }
          compositeBuildData.getCompositeParticipants().add(buildParticipant);
        }
      }
      projectDataNode.createChild(CompositeBuildData.KEY, compositeBuildData);
    }
    return gradleIncludedModules;
  }

  private static void mergeLibraryAndModuleDependencyData(@NotNull ProjectResolverContext context,
                                                          @NotNull DataNode<ProjectData> projectDataNode,
                                                          @NotNull File gradleUserHomeDir,
                                                          @Nullable File gradleHomeDir,
                                                          @Nullable GradleVersion gradleVersion) {
    final Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap =
      projectDataNode.getUserData(RESOLVED_SOURCE_SETS);
    assert sourceSetMap != null;

    final Map<String, Pair<String, ExternalSystemSourceType>> moduleOutputsMap =
      projectDataNode.getUserData(MODULES_OUTPUTS);
    assert moduleOutputsMap != null;

    final ArtifactMappingService artifactsMap = context.getArtifactsMap();
    assert artifactsMap != null;

    final Collection<DataNode<LibraryDependencyData>> libraryDependencies =
      findAllRecursively(projectDataNode, ProjectKeys.LIBRARY_DEPENDENCY);

    LibraryDataNodeSubstitutor librarySubstitutor = new LibraryDataNodeSubstitutor(
      context, gradleUserHomeDir, gradleHomeDir, gradleVersion, sourceSetMap, moduleOutputsMap, artifactsMap);
    for (DataNode<LibraryDependencyData> libraryDependencyDataNode : libraryDependencies) {
      librarySubstitutor.run(libraryDependencyDataNode);
    }
  }

  private static void extractExternalProjectModels(@NotNull ProjectImportAction.AllModels models,
                                                   @NotNull ProjectResolverContext resolverCtx,
                                                   boolean useCustomSerialization) {
    resolverCtx.setModels(models);
    final Class<? extends ExternalProject> modelClazz = resolverCtx.isPreviewMode() ? ExternalProjectPreview.class : ExternalProject.class;
    final ExternalProject externalRootProject = models.getModel(modelClazz);
    if (externalRootProject == null) return;

    final DefaultExternalProject wrappedExternalRootProject =
      useCustomSerialization ? (DefaultExternalProject)externalRootProject : new DefaultExternalProject(externalRootProject);
    models.addModel(wrappedExternalRootProject, ExternalProject.class);
    final Map<String, DefaultExternalProject> externalProjectsMap = createExternalProjectsMap(wrappedExternalRootProject);

    Collection<Project> projects = models.getMainBuild().getProjects();
    for (Project project : projects) {
      ExternalProject externalProject = externalProjectsMap.get(project.getProjectIdentifier().getProjectPath());
      if (externalProject != null) {
        models.addModel(externalProject, ExternalProject.class, project);
      }
    }

    for (Build includedBuild : models.getIncludedBuilds()) {
      final ExternalProject externalIncludedRootProject = models.getModel(includedBuild, modelClazz);
      if (externalIncludedRootProject == null) continue;
      final DefaultExternalProject wrappedExternalIncludedRootProject = useCustomSerialization
                                                                        ? (DefaultExternalProject)externalIncludedRootProject
                                                                        : new DefaultExternalProject(externalIncludedRootProject);
      wrappedExternalRootProject.getChildProjects().put(wrappedExternalIncludedRootProject.getName(), wrappedExternalIncludedRootProject);
      final Map<String, DefaultExternalProject> externalIncludedProjectsMap = createExternalProjectsMap(wrappedExternalIncludedRootProject);
      for (ProjectModel project : includedBuild.getProjects()) {
        ExternalProject externalProject = externalIncludedProjectsMap.get(project.getProjectIdentifier().getProjectPath());
        if (externalProject != null) {
          models.addModel(externalProject, ExternalProject.class, project);
        }
      }
    }
  }

  @NotNull
  private static Map<String, DefaultExternalProject> createExternalProjectsMap(@Nullable DefaultExternalProject rootExternalProject) {
    final Map<String, DefaultExternalProject> externalProjectMap = new HashMap<>();
    if (rootExternalProject == null) return externalProjectMap;
    ArrayDeque<DefaultExternalProject> queue = new ArrayDeque<>();
    queue.add(rootExternalProject);
    DefaultExternalProject externalProject;
    while ((externalProject = queue.pollFirst()) != null) {
      queue.addAll(externalProject.getChildProjects().values());
      externalProjectMap.put(externalProject.getQName(), externalProject);
    }
    return externalProjectMap;
  }

  private static class Counter {
    int count;

    void increment() {
      count++;
    }

    @Override
    public String toString() {
      return String.valueOf(count);
    }
  }

  private static void mergeSourceSetContentRoots(@NotNull Map<String, Pair<DataNode<ModuleData>, IdeaModule>> moduleMap,
                                                 @NotNull ProjectResolverContext resolverCtx) {

    final Map<String, Counter> weightMap = new HashMap<>();
    for (final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values()) {
      final DataNode<ModuleData> moduleNode = pair.first;
      for (DataNode<ContentRootData> contentRootNode : findAll(moduleNode, ProjectKeys.CONTENT_ROOT)) {
        File file = new File(contentRootNode.getData().getRootPath());
        while (file != null) {
          weightMap.computeIfAbsent(file.getPath(), __ -> new Counter()).increment();
          file = file.getParentFile();
        }
      }

      for (DataNode<GradleSourceSetData> sourceSetNode : findAll(moduleNode, GradleSourceSetData.KEY)) {
        final Set<String> set = new HashSet<>();
        for (DataNode<ContentRootData> contentRootNode : findAll(sourceSetNode, ProjectKeys.CONTENT_ROOT)) {
          File file = new File(contentRootNode.getData().getRootPath());
          while (file != null) {
            set.add(file.getPath());
            file = file.getParentFile();
          }
        }
        for (String path : set) {
          weightMap.computeIfAbsent(path, __ -> new Counter()).increment();
        }
      }
    }
    for (final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values()) {
      final DataNode<ModuleData> moduleNode = pair.first;
      final ExternalProject externalProject = resolverCtx.getExtraProject(pair.second, ExternalProject.class);
      if (externalProject == null) continue;

      mergeModuleContentRoots(weightMap, externalProject, moduleNode);
      for (DataNode<GradleSourceSetData> sourceSetNode : findAll(moduleNode, GradleSourceSetData.KEY)) {
        mergeModuleContentRoots(weightMap, externalProject, sourceSetNode);
      }
    }
  }

  private static void mergeModuleContentRoots(@NotNull Map<String, Counter> weightMap,
                                              @NotNull ExternalProject externalProject,
                                              @NotNull DataNode<? extends ModuleData> moduleNode) {
    final File buildDir = externalProject.getBuildDir();
    final MultiMap<String, ContentRootData> sourceSetRoots = MultiMap.create();
    Collection<DataNode<ContentRootData>> contentRootNodes = findAll(moduleNode, ProjectKeys.CONTENT_ROOT);
    if (contentRootNodes.size() <= 1) return;

    for (DataNode<ContentRootData> contentRootNode : contentRootNodes) {
      File root = new File(contentRootNode.getData().getRootPath());
      if (FileUtil.isAncestor(buildDir, root, true)) continue;

      while (weightMap.containsKey(root.getParent()) && weightMap.get(root.getParent()).count <= 1) {
        root = root.getParentFile();
      }

      ContentRootData mergedContentRoot = null;
      String rootPath = toCanonicalPath(root.getPath());
      Set<String> paths = new HashSet<>(sourceSetRoots.keySet());
      for (String path : paths) {
        if (FileUtil.isAncestor(rootPath, path, true)) {
          Collection<ContentRootData> values = sourceSetRoots.remove(path);
          if (values != null) {
            sourceSetRoots.putValues(rootPath, values);
          }
        }
        else if (FileUtil.isAncestor(path, rootPath, false)) {
          Collection<ContentRootData> contentRoots = sourceSetRoots.get(path);
          for (ContentRootData rootData : contentRoots) {
            if (StringUtil.equals(rootData.getRootPath(), path)) {
              mergedContentRoot = rootData;
              break;
            }
          }
          if (mergedContentRoot == null) {
            mergedContentRoot = contentRoots.iterator().next();
          }
          break;
        }
        if (sourceSetRoots.size() == 1) break;
      }

      if (mergedContentRoot == null) {
        mergedContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, root.getPath());
        sourceSetRoots.putValue(mergedContentRoot.getRootPath(), mergedContentRoot);
      }

      for (ExternalSystemSourceType sourceType : ExternalSystemSourceType.values()) {
        for (ContentRootData.SourceRoot sourceRoot : contentRootNode.getData().getPaths(sourceType)) {
          mergedContentRoot.storePath(sourceType, sourceRoot.getPath(), sourceRoot.getPackagePrefix());
        }
      }

      contentRootNode.clear(true);
    }

    for (Map.Entry<String, Collection<ContentRootData>> entry : sourceSetRoots.entrySet()) {
      final String rootPath = entry.getKey();
      final ContentRootData ideContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, rootPath);

      for (ContentRootData rootData : entry.getValue()) {
        for (ExternalSystemSourceType sourceType : ExternalSystemSourceType.values()) {
          Collection<ContentRootData.SourceRoot> roots = rootData.getPaths(sourceType);
          for (ContentRootData.SourceRoot sourceRoot : roots) {
            ideContentRoot.storePath(sourceType, sourceRoot.getPath(), sourceRoot.getPackagePrefix());
          }
        }
      }

      moduleNode.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot);
    }
  }

  private final class ProjectConnectionDataNodeFunction implements Function<ProjectConnection, DataNode<ProjectData>> {
    @NotNull private final GradleProjectResolverExtension myProjectResolverChain;
    private final boolean myIsBuildSrcProject;
    private final DefaultProjectResolverContext myResolverContext;

    private ProjectConnectionDataNodeFunction(@NotNull DefaultProjectResolverContext resolverContext,
                                              @NotNull GradleProjectResolverExtension projectResolverChain, boolean isBuildSrcProject) {
      myResolverContext = resolverContext;
      myProjectResolverChain = projectResolverChain;
      myIsBuildSrcProject = isBuildSrcProject;
    }

    @Override
    public DataNode<ProjectData> fun(ProjectConnection connection) {
      ExternalSystemTaskId taskId = myResolverContext.getExternalSystemTaskId();
      final long activityId = taskId.getId();
      try {
        myCancellationMap.putValue(taskId, myResolverContext.getCancellationTokenSource());
        myResolverContext.setConnection(connection);
        return doResolveProjectInfo(myResolverContext, myProjectResolverChain, myIsBuildSrcProject);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (RuntimeException e) {
        LOG.info("Gradle project resolve error", e);
        ExternalSystemException esException = ExceptionUtil.findCause(e, ExternalSystemException.class);
        if (esException != null && esException != e) {
          LOG.info("\nCaused by: " + esException.getOriginalReason());
        }
        ExternalSystemSyncActionsCollector.logError(taskId.findProject(), activityId, extractCause(e));
        ExternalSystemSyncActionsCollector.logSyncFinished(taskId.findProject(), activityId, false);
        syncMetrics.endSpan(ExternalSystemSyncDiagnostic.gradleSyncSpanName, (span) -> span.setAttribute("project", ""));

        throw myProjectResolverChain.getUserFriendlyError(
          myResolverContext.getBuildEnvironment(), e, myResolverContext.getProjectPath(), null);
      }
      finally {
        myCancellationMap.remove(taskId, myResolverContext.getCancellationTokenSource());
      }
    }
  }

  private static Throwable extractCause(Throwable e) {
    if (e instanceof BuildActionFailureException) {
      return extractCause(e.getCause());
    }
    // Exceptions returned by Gradle TAPI have classes loaded by a separate classloader
    Class<? extends Throwable> exceptionClass = e.getClass();
    if (exceptionClass.getName().equals(ProjectConfigurationException.class.getName())) {
      try {
        //noinspection unchecked
        List<Throwable> causes = (List<Throwable>)exceptionClass.getMethod("getCauses").invoke(e);
        return causes.get(0);
      }
      catch (Throwable ignore) {
        return e;
      }
    }
    return e;
  }

  @ApiStatus.Experimental // chaining of resolver extensions complicates things and can be replaced in future
  public static GradleProjectResolverExtension createProjectResolverChain() {
    return createProjectResolverChain(null, null);
  }

  @NotNull
  private static GradleProjectResolverExtension createProjectResolverChain(@Nullable DefaultProjectResolverContext resolverContext,
                                                                           @Nullable Predicate<? super GradleProjectResolverExtension> extensionsFilter) {
    Stream<GradleProjectResolverExtension> extensions = GradleProjectResolverUtil.createProjectResolvers(resolverContext);
    if (extensionsFilter != null) {
      extensions = extensions.filter(extensionsFilter.or(BaseResolverExtension.class::isInstance));
    }

    Deque<GradleProjectResolverExtension> deque = new ArrayDeque<>();
    extensions.forEachOrdered(extension -> {
      final GradleProjectResolverExtension previous = deque.peekLast();
      if (previous != null) {
        previous.setNext(extension);
        if (previous.getNext() != extension) {
          throw new AssertionError("Illegal next resolver got, current resolver class is " + previous.getClass().getName());
        }
      }
      deque.add(extension);
    });

    GradleProjectResolverExtension firstResolver = deque.peekFirst();
    GradleProjectResolverExtension resolverExtension = firstResolver;
    assert resolverExtension != null;
    while (resolverExtension.getNext() != null) {
      resolverExtension = resolverExtension.getNext();
    }
    if (!(resolverExtension instanceof BaseResolverExtension)) {
      throw new AssertionError("Illegal last resolver got of class " + resolverExtension.getClass().getName());
    }

    GradleProjectResolverExtension chainWrapper = new AbstractProjectResolverExtension() {
      @NotNull
      @Override
      public ExternalSystemException getUserFriendlyError(@Nullable BuildEnvironment buildEnvironment,
                                                          @NotNull Throwable error,
                                                          @NotNull String projectPath,
                                                          @Nullable String buildFilePath) {
        ExternalSystemException friendlyError = super.getUserFriendlyError(buildEnvironment, error, projectPath, buildFilePath);
        return new BaseProjectImportErrorHandler()
          .checkErrorsWithoutQuickFixes(buildEnvironment, error, projectPath, buildFilePath, friendlyError);
      }
    };
    chainWrapper.setNext(firstResolver);
    return chainWrapper;
  }

  private static @NotNull GradleTracingContext getActionTelemetryContext(@NotNull Span span) {
    GradleTracingContext context = new GradleTracingContext();
    GlobalOpenTelemetry.get()
      .getPropagators()
      .getTextMapPropagator()
      .inject(Context.current().with(span), context, GradleTracingContext.SETTER);
    return context;
  }

  private static void exportRecordedTraces(@NotNull ProjectImportAction.AllModels allModels) {
    byte[] traces = allModels.getOpenTelemetryTrace();
    if (traces.length == 0) {
      return;
    }
    URI telemetryHost = getOpenTelemetryAddress();
    if (telemetryHost == null) {
      return;
    }
    ApplicationManager.getApplication()
      .executeOnPooledThread(() -> GradleOpenTelemetryTraceExporter.export(telemetryHost, traces));
  }

  private static @Nullable URI getOpenTelemetryAddress() {
    String property = System.getProperty("idea.diagnostic.opentelemetry.otlp");
    if (property == null) {
      return null;
    }
    if (property.endsWith("/")) {
      return URI.create(property + "v1/traces");
    }
    return URI.create(property + "/v1/traces");
  }
}
