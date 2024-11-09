// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.build.events.MessageEvent;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel.DefaultGradleSourceSetDependencyModel;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.DefaultGradleSourceSetModel;
import com.intellij.gradle.toolingExtension.impl.model.taskModel.DefaultGradleTaskModel;
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction;
import com.intellij.gradle.toolingExtension.impl.util.GradleTreeTraverserUtil;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemOperationDescriptor;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemSyncActionsCollector;
import com.intellij.openapi.externalSystem.statistics.Phase;
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.CanonicalPathPrefixTreeFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioPathUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.tooling.BuildActionFailureException;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
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
import org.jetbrains.plugins.gradle.service.execution.GradleWrapperHelper;
import org.jetbrains.plugins.gradle.service.modelAction.GradleIdeaModelHolder;
import org.jetbrains.plugins.gradle.service.modelAction.GradleModelFetchActionRunner;
import org.jetbrains.plugins.gradle.service.syncAction.GradleModelFetchActionResultHandler;
import org.jetbrains.plugins.gradle.service.syncAction.GradleProjectResolverResultHandler;
import org.jetbrains.plugins.gradle.settings.GradleBuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleDataKt;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static org.jetbrains.plugins.gradle.service.project.ArtifactMappingServiceKt.OWNER_BASE_GRADLE;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleId;

/**
 * @author Vladislav Soroka
 */
public class GradleProjectResolver implements ExternalSystemProjectResolver<GradleExecutionSettings> {

  private static final Logger LOG = Logger.getInstance(GradleProjectResolver.class);

  private final @NotNull GradleExecutionHelper myHelper;
  private final @NotNull GradleLibraryNamesMixer myLibraryNamesMixer = new GradleLibraryNamesMixer();
  private final @NotNull MultiMap<ExternalSystemTaskId, CancellationTokenSource> myCancellationMap = MultiMap.createConcurrent();

  public static final Key<Map<String/* module id */, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>>> RESOLVED_SOURCE_SETS =
    Key.create("resolvedSourceSets");
  public static final Key<Map<String/* output path */, Pair<String /* module id*/, ExternalSystemSourceType>>> MODULES_OUTPUTS =
    Key.create("moduleOutputsMap");
  public static final Key<MultiMap<ExternalSystemSourceType, String /* output path*/>> GRADLE_OUTPUTS =
    Key.create("gradleOutputs");

  private static final Key<File> GRADLE_HOME_DIR = Key.create("gradleHomeDir");

  public static final boolean DEBUG_ORPHAN_MODULES_PROCESSING = Boolean.getBoolean("external.system.debug.orphan.modules.processing");

  // This constructor is called by external system API, see AbstractExternalSystemFacadeImpl class constructor.
  @SuppressWarnings("UnusedDeclaration")
  public GradleProjectResolver() {
    this(new GradleExecutionHelper());
  }

  public GradleProjectResolver(@NotNull GradleExecutionHelper helper) {
    myHelper = helper;
  }

  @Override
  public @Nullable DataNode<ProjectData> resolveProjectInfo(
    @NotNull ExternalSystemTaskId syncTaskId,
    @NotNull String projectPath,
    boolean isPreviewMode,
    @Nullable GradleExecutionSettings settings,
    @Nullable ProjectResolverPolicy resolverPolicy,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException, IllegalArgumentException, IllegalStateException {

    GradlePartialResolverPolicy gradleResolverPolicy = null;
    if (resolverPolicy != null) {
      if (resolverPolicy instanceof GradlePartialResolverPolicy) {
        gradleResolverPolicy = (GradlePartialResolverPolicy)resolverPolicy;
      }
      else {
        throw new ExternalSystemException("Unsupported project resolver policy: " + resolverPolicy.getClass().getName());
      }
    }

    GradleProjectResolverIndicator projectResolverIndicator = new GradleProjectResolverIndicator(
      ProgressManager.getInstance().getProgressIndicator(),
      GradleConnector.newCancellationTokenSource()
    );
    DefaultProjectResolverContext resolverContext = new DefaultProjectResolverContext(
      syncTaskId, projectPath, settings, listener, gradleResolverPolicy, projectResolverIndicator, false
    );
    GradleProjectResolverResultHandler resolverResultHandler = new GradleProjectResolverResultHandler(resolverContext);

    return computeCancellable(resolverContext, () -> {
      // Create project preview model w/o request to gradle, there are two main reasons for the it:
      // * Slow project open - even the simplest project info provided by gradle can be gathered too long (mostly because of new gradle distribution download and downloading build script dependencies)
      // * Ability to open  an invalid projects (e.g. with errors in build scripts)
      if (isPreviewMode) {
        return GradlePreviewCustomizer.Companion.getCustomizer(resolverContext)
          .resolvePreviewProjectInfo(resolverContext);
      }

      resolverResultHandler.onResolveProjectInfoStarted();

      return resolveProjectInfo(resolverContext);
    });
  }

  private @Nullable DataNode<ProjectData> resolveProjectInfo(
    @NotNull DefaultProjectResolverContext resolverContext
  ) {
    final long activityId = resolverContext.getExternalSystemTaskId().getId();
    GradleExecutionSettings settings = resolverContext.getSettings();
    ExternalSystemSyncActionsCollector.logSyncStarted(resolverContext.getExternalSystemTaskId().findProject(), activityId,
                                                      settings != null && settings.isParallelModelFetch());

    Span gradleExecutionSpan = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("GradleExecution")
      .startSpan();
    try (Scope ignore = gradleExecutionSpan.makeCurrent()) {
      if (resolverContext.getSettings() != null) {
        GradleWrapperHelper.ensureInstalledWrapper(
          resolverContext.getExternalSystemTaskId(),
          resolverContext.getProjectPath(),
          resolverContext.getSettings(),
          resolverContext.getListener(),
          resolverContext.getCancellationToken()
        );
      }

      final GradleProjectResolverExtension projectResolverChain = createProjectResolverChain(resolverContext);
      var projectDataFunction = getProjectDataFunction(resolverContext, projectResolverChain);
      final DataNode<ProjectData> projectDataNode = myHelper.execute(
        resolverContext.getProjectPath(),
        resolverContext.getSettings(),
        resolverContext.getExternalSystemTaskId(),
        resolverContext.getListener(),
        resolverContext.getCancellationToken(),
        projectDataFunction
      );

      // auto-discover buildSrc projects of the main and included builds
      String gradleVersion = resolverContext.getProjectGradleVersion();
      if (gradleVersion != null && GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.0")) {
        String gradleHome = ObjectUtils.doIfNotNull(resolverContext.getUserData(GRADLE_HOME_DIR), it -> it.getPath());
        new GradleBuildSrcProjectsResolver(this, resolverContext, gradleHome, projectResolverChain)
          .discoverAndAppendTo(projectDataNode);
      }

      return projectDataNode;
    }
    finally {
      gradleExecutionSpan.end();
    }
  }


  protected @NotNull Function<ProjectConnection, DataNode<ProjectData>> getProjectDataFunction(
    @NotNull DefaultProjectResolverContext resolverContext,
    @NotNull GradleProjectResolverExtension projectResolverChain
  ) {
    return new ProjectConnectionDataNodeFunction(resolverContext, projectResolverChain);
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

  private <R> R computeCancellable(@NotNull DefaultProjectResolverContext resolverContext, @NotNull Supplier<R> action) {
    ExternalSystemTaskId taskId = resolverContext.getExternalSystemTaskId();
    CancellationTokenSource cancellationTokenSource = resolverContext.getCancellationTokenSource();
    myCancellationMap.putValue(taskId, cancellationTokenSource);
    try {
      return resolverContext.computeCancellable(action);
    }
    finally {
      myCancellationMap.remove(taskId, cancellationTokenSource);
    }
  }

  @NotNull
  private DataNode<ProjectData> doResolveProjectInfo(
    @NotNull final DefaultProjectResolverContext resolverContext,
    @NotNull final GradleProjectResolverExtension projectResolverChain
  )
    throws IllegalArgumentException, IllegalStateException {

    final BuildEnvironment buildEnvironment = GradleExecutionHelper.getBuildEnvironment(
      resolverContext.getConnection(),
      resolverContext.getExternalSystemTaskId(),
      resolverContext.getListener(),
      resolverContext.getCancellationToken(),
      resolverContext.getSettings()
    );
    if (buildEnvironment != null) {
      resolverContext.setBuildEnvironment(buildEnvironment);
    }

    var buildAction = new GradleModelFetchAction();

    GradleExecutionSettings executionSettings = resolverContext.getSettings();
    if (executionSettings == null) {
      executionSettings = new GradleExecutionSettings();
    }

    configureExecutionArgumentsAndVmOptions(executionSettings, resolverContext);
    final Set<Class<?>> toolingExtensionClasses = new HashSet<>();
    for (GradleProjectResolverExtension resolverExtension = projectResolverChain;
         resolverExtension != null;
         resolverExtension = resolverExtension.getNext()) {
      // inject ProjectResolverContext into gradle project resolver extensions
      resolverExtension.setProjectResolverContext(resolverContext);
      // pre-import checks
      resolverExtension.preImportCheck();

      buildAction.addTargetTypes(resolverExtension.getTargetTypes());

      // register classes of extra gradle project models required for extensions (e.g. com.android.builder.model.AndroidProject)
      try {
        buildAction.addProjectImportModelProviders(resolverExtension.getModelProviders());
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

    GradleInitScriptUtil.attachTargetPathMapperInitScript(executionSettings);
    var initScript = GradleInitScriptUtil.createMainInitScript(resolverContext.isBuildSrcProject(), toolingExtensionClasses);
    executionSettings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.toString());

    if (!executionSettings.isDownloadSources()) {
      Path initScriptPath = GradleInitScriptUtil.createIdeaPluginConfiguratorInitScript();
      executionSettings.prependArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScriptPath.toString());
    }

    var environmentConfigurationProvider = ExternalSystemExecutionAware.getEnvironmentConfigurationProvider(executionSettings);
    var pathMapper = ObjectUtils.doIfNotNull(environmentConfigurationProvider, it -> it.getPathMapper());
    var models = new GradleIdeaModelHolder(pathMapper, buildEnvironment);
    resolverContext.setModels(models);


    ProgressManager.checkCanceled();

    final long activityId = resolverContext.getExternalSystemTaskId().getId();

    final long gradleCallStartTime = System.currentTimeMillis();

    ExternalSystemSyncActionsCollector.logPhaseStarted(null, activityId, Phase.GRADLE_CALL);

    int gradleCallErrorsCount = 0;

    Span gradleCallSpan = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("GradleCall")
      .startSpan();
    try (Scope ignore = gradleCallSpan.makeCurrent()) {
      var modelFetchActionResultHandler = new GradleModelFetchActionResultHandler(resolverContext);
      GradleModelFetchActionRunner.runAndTraceBuildAction(resolverContext, executionSettings, buildAction, modelFetchActionResultHandler);

      var gradleVersion = ObjectUtils.doIfNotNull(resolverContext.getProjectGradleVersion(), it -> GradleVersion.version(it));
      if (gradleVersion != null && GradleJvmSupportMatrix.isGradleDeprecatedByIdea(gradleVersion)) {
        var projectPath = resolverContext.getProjectPath();
        var issue = new DeprecatedGradleVersionIssue(gradleVersion, projectPath);
        resolverContext.report(MessageEvent.Kind.WARNING, issue);
      }
    }
    catch (Throwable t) {
      gradleCallErrorsCount += 1;
      gradleCallSpan.setAttribute("error.count", gradleCallErrorsCount);
      gradleCallSpan.recordException(t);
      gradleCallSpan.setStatus(StatusCode.ERROR);
      throw t;
    }
    finally {
      final long gradleCallTimeInMs = (System.currentTimeMillis() - gradleCallStartTime);
      ExternalSystemSyncActionsCollector.logPhaseFinished(
        null, activityId, Phase.GRADLE_CALL, gradleCallTimeInMs, gradleCallErrorsCount);
      gradleCallSpan.end();
    }

    ProgressManager.checkCanceled();

    final long projectResolversStartTime = System.currentTimeMillis();

    ExternalSystemSyncActionsCollector.logPhaseStarted(null, activityId, Phase.PROJECT_RESOLVERS);

    int projectResolversErrorsCount = 0;

    Span projectResolversSpan = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("GradleProjectResolverDataProcessing")
      .startSpan();
    try (Scope ignore = projectResolversSpan.makeCurrent()) {
      extractExternalProjectModels(models);
      return convertData(executionSettings, resolverContext, projectResolverChain);
    }
    catch (Throwable t) {
      projectResolversErrorsCount += 1;
      projectResolversSpan.recordException(t);
      projectResolversSpan.setStatus(StatusCode.ERROR);
      throw t;
    }
    finally {
      final long projectResolversTimeInMs = (System.currentTimeMillis() - projectResolversStartTime);
      LOG.debug(String.format("Project data resolved in %d ms", projectResolversTimeInMs));
      ExternalSystemSyncActionsCollector.logPhaseFinished(
        null, activityId, Phase.PROJECT_RESOLVERS, projectResolversTimeInMs, projectResolversErrorsCount);
      projectResolversSpan.end();
    }
  }

  private @NotNull DataNode<ProjectData> convertData(
    @NotNull GradleExecutionSettings executionSettings,
    @NotNull DefaultProjectResolverContext resolverContext,
    @NotNull GradleProjectResolverExtension tracedResolverChain
  ) {
    final long activityId = resolverContext.getExternalSystemTaskId().getId();

    String projectPath = resolverContext.getProjectPath();
    String projectName = resolverContext.getRootBuild().getName();

    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectName, projectPath, projectPath);
    DataNode<ProjectData> projectDataNode = new DataNode<>(ProjectKeys.PROJECT, projectData, null);
    DataNode<ExternalSystemOperationDescriptor> descriptorDataNode = new DataNode<>(ExternalSystemOperationDescriptor.OPERATION_DESCRIPTOR_KEY,
                                                                                    new ExternalSystemOperationDescriptor(activityId),
                                                                                    projectDataNode);
    projectDataNode.addChild(descriptorDataNode);

    final Set<? extends IdeaModule> gradleModules = extractCollectedModules(resolverContext, projectDataNode, tracedResolverChain);
    final Collection<IdeaModule> includedModules = exposeCompositeBuild(resolverContext, projectDataNode);

    final Map<String /* module id */, Pair<DataNode<ModuleData>, IdeaModule>> moduleMap = new HashMap<>();
    final Map<String /* module id */, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetsMap = new HashMap<>();
    projectDataNode.putUserData(RESOLVED_SOURCE_SETS, sourceSetsMap);

    final Map<String/* output path */, Pair<String /* module id*/, ExternalSystemSourceType>> moduleOutputsMap =
      CollectionFactory.createFilePathMap();
    projectDataNode.putUserData(MODULES_OUTPUTS, moduleOutputsMap);

    final ArtifactMappingService artifactsMap = resolverContext.getArtifactsMap();

    // import modules data
    for (IdeaModule gradleModule : ContainerUtil.concat(gradleModules, includedModules)) {
      ProgressManager.checkCanceled();
      DataNode<ModuleData> moduleDataNode = createModuleData(gradleModule, tracedResolverChain, projectDataNode);
      if (moduleDataNode == null) {
        continue;
      }
      String mainModuleId = getModuleId(resolverContext, gradleModule);
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
        final GradleBuildScriptClasspathModel buildScriptClasspathModel =
          resolverContext.getExtraProject(ideaModule, GradleBuildScriptClasspathModel.class);
        if (buildScriptClasspathModel != null) {
          gradleHomeDir = buildScriptClasspathModel.getGradleHomeDir();
        }
      }

      tracedResolverChain.populateModuleContentRoots(ideaModule, moduleDataNode);
      tracedResolverChain.populateModuleCompileOutputSettings(ideaModule, moduleDataNode);
      if (!resolverContext.isBuildSrcProject()) {
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

      GradleSourceSetModel sourceSetModel = resolverContext.getProjectModel(ideaModule, GradleSourceSetModel.class);
      if (sourceSetModel != null) {
        sourceSetModel.getAdditionalArtifacts().forEach((artifactFile) -> {
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
    resolverContext.putUserData(GRADLE_HOME_DIR, gradleHomeDir);

    ExternalSystemTelemetryUtil.runWithSpan(GradleConstants.SYSTEM_ID, "PopulateModules", __ -> {
      for (final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values()) {
        final DataNode<ModuleData> moduleDataNode = pair.first;
        final IdeaModule ideaModule = pair.second;
        tracedResolverChain.populateModuleDependencies(ideaModule, moduleDataNode, projectDataNode);
        tracedResolverChain.populateModuleExtraModels(ideaModule, moduleDataNode);
      }
    });

    mergeSourceSetContentRoots(resolverContext, moduleMap);
    if (resolverContext.isResolveModulePerSourceSet()) {
      mergeLibraryAndModuleDependencyData(resolverContext, projectDataNode, resolverContext.getGradleUserHome(), gradleHomeDir);
    }

    processBuildSrcModules(resolverContext, projectDataNode);

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

  private static @Nullable DataNode<ModuleData> createModuleData(@Nullable IdeaModule gradleModule,
                                                                 @NotNull GradleProjectResolverExtension resolverChain,
                                                                 @NotNull DataNode<ProjectData> projectDataNode) {
    if (gradleModule == null) {
      return null;
    }
    if (DEBUG_ORPHAN_MODULES_PROCESSING) {
      LOG.info(String.format("Importing module data: %s", gradleModule));
    }
    final String moduleName = gradleModule.getName();
    if (moduleName == null) {
      throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
    }
    return resolverChain.createModule(gradleModule, projectDataNode);
  }

  private static @NotNull Set<? extends IdeaModule> extractCollectedModules(
    @NotNull DefaultProjectResolverContext resolverContext,
    @NotNull DataNode<ProjectData> projectDataNode,
    @NotNull GradleProjectResolverExtension resolverChain
  ) {
    IdeaProject ideaProject = resolverContext.getRootModel(IdeaProject.class);
    if (ideaProject == null) {
      return Collections.emptySet();
    }
    resolverChain.populateProjectExtraModels(ideaProject, projectDataNode);
    Set<? extends IdeaModule> modules = ideaProject.getModules();
    if (modules == null || modules.isEmpty()) {
      throw new IllegalStateException("No modules found for the target project: " + ideaProject);
    }
    return modules;
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
            GradleModuleDataKt.setBuildSrcModule(node.getData());
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

  private static void configureExecutionArgumentsAndVmOptions(
    @NotNull GradleExecutionSettings executionSettings,
    @NotNull DefaultProjectResolverContext resolverContext
  ) {
    executionSettings.withArgument("-Didea.gradle.download.sources=" + executionSettings.isDownloadSources());
    executionSettings.withArgument("-Didea.sync.active=true");
    if (resolverContext.isResolveModulePerSourceSet()) {
      executionSettings.withArgument("-Didea.resolveSourceSetDependencies=true");
    }
    if (executionSettings.isParallelModelFetch()) {
      executionSettings.withArgument("-Didea.parallelModelFetch.enabled=true");
    }
    if (!resolverContext.isBuildSrcProject()) {
      for (GradleBuildParticipant buildParticipant : executionSettings.getExecutionWorkspace().getBuildParticipants()) {
        executionSettings.withArguments(GradleConstants.INCLUDE_BUILD_CMD_OPTION, buildParticipant.getProjectPath());
      }
    }
    if (Registry.is("gradle.daemon.experimental.dependency.resolver", false)) {
      executionSettings.withArgument("-Didea.experimental.gradle.dependency.resolver=true");
    }

    GradleImportCustomizer importCustomizer = GradleImportCustomizer.get();
    GradleProjectResolverUtil.createProjectResolvers(resolverContext).forEachOrdered(extension -> {
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

  private static @NotNull Collection<IdeaModule> exposeCompositeBuild(
    @NotNull DefaultProjectResolverContext resolverContext,
    @NotNull DataNode<ProjectData> projectDataNode
  ) {
    if (resolverContext.getSettings() != null && !resolverContext.getSettings().getExecutionWorkspace().getBuildParticipants().isEmpty()) {
      return Collections.emptyList();
    }
    CompositeBuildData compositeBuildData;
    List<IdeaModule> gradleIncludedModules = new SmartList<>();
    var includedBuilds = resolverContext.getNestedBuilds();
    if (!includedBuilds.isEmpty()) {
      ProjectData projectData = projectDataNode.getData();
      compositeBuildData = new CompositeBuildData(projectData.getLinkedExternalProjectPath());
      for (GradleLightBuild build : includedBuilds) {
        if (!build.getProjects().isEmpty()) {
          IdeaProject ideaProject = resolverContext.getBuildModel(build, IdeaProject.class);
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

  private static void mergeLibraryAndModuleDependencyData(
    @NotNull ProjectResolverContext context,
    @NotNull DataNode<ProjectData> projectDataNode,
    @NotNull File gradleUserHomeDir,
    @Nullable File gradleHomeDir
  ) {
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
      context, gradleUserHomeDir, gradleHomeDir, sourceSetMap, moduleOutputsMap, artifactsMap);
    for (DataNode<LibraryDependencyData> libraryDependencyDataNode : libraryDependencies) {
      librarySubstitutor.run(libraryDependencyDataNode);
    }
  }

  private static void extractExternalProjectModels(@NotNull GradleIdeaModelHolder models) {
    replicateBuildModelHierarchyInExternalProjectHierarchy(models);
    replicateProjectModelHierarchyInExternalProjectHierarchy(models);
    associateSourceSetModelsWithExternalProjects(models);
    associateSourceSetDependencyModelsWithSourceSetModels(models);
    registerInheritedTaskModelsInParentTaskModel(models);
    associateTaskModelsWithExternalProjects(models);
  }

  private static void replicateBuildModelHierarchyInExternalProjectHierarchy(@NotNull GradleIdeaModelHolder models) {
    var rootBuildModel = models.getRootBuild();
    var rootProjectModel = rootBuildModel.getRootProject();
    var rootExternalProject = (DefaultExternalProject)models.getProjectModel(rootProjectModel, ExternalProject.class);
    if (rootExternalProject == null) return;
    for (var nestedBuildModel : models.getNestedBuilds()) {
      var projectModel = nestedBuildModel.getRootProject();
      var externalProject = (DefaultExternalProject)models.getProjectModel(projectModel, ExternalProject.class);
      if (externalProject == null) continue;
      rootExternalProject.addChildProject(externalProject);
    }
  }

  private static void replicateProjectModelHierarchyInExternalProjectHierarchy(@NotNull GradleIdeaModelHolder models) {
    for (var buildModel : models.getAllBuilds()) {
      DefaultGradleLightBuild.replicateModelHierarchy(
        buildModel.getRootProject(),
        it -> (DefaultExternalProject)models.getProjectModel(it, ExternalProject.class),
        GradleLightProject::getChildProjects,
        DefaultExternalProject::addChildProject
      );
    }
  }

  private static void associateSourceSetModelsWithExternalProjects(@NotNull GradleIdeaModelHolder models) {
    for (var buildModel : models.getAllBuilds()) {
      for (var projectModel : buildModel.getProjects()) {
        var externalProject = (DefaultExternalProject)models.getProjectModel(projectModel, ExternalProject.class);
        var sourceSetModel = (DefaultGradleSourceSetModel)models.getProjectModel(projectModel, GradleSourceSetModel.class);
        if (externalProject == null || sourceSetModel == null) continue;

        externalProject.setSourceSetModel(sourceSetModel);
      }
    }
  }

  private static void associateSourceSetDependencyModelsWithSourceSetModels(@NotNull GradleIdeaModelHolder models) {
    for (var buildModel : models.getAllBuilds()) {
      for (var projectModel : buildModel.getProjects()) {
        var sourceSetModel = (DefaultGradleSourceSetModel)models.getProjectModel(projectModel, GradleSourceSetModel.class);
        var sourceSetDependencyModel = (DefaultGradleSourceSetDependencyModel)models.getProjectModel(projectModel, GradleSourceSetDependencyModel.class);
        if (sourceSetModel == null || sourceSetDependencyModel == null) continue;

        var sourceSets = sourceSetModel.getSourceSets();
        var dependencies = sourceSetDependencyModel.getDependencies();
        var sourceSetNames = new LinkedHashSet<>(sourceSets.keySet());
        sourceSetNames.retainAll(dependencies.keySet());
        for (String sourceSetName : sourceSetNames) {
          var sourceSet = sourceSets.get(sourceSetName);
          var sourceSetDependencies = dependencies.get(sourceSetName);
          sourceSet.setDependencies(sourceSetDependencies);
        }
      }
    }
  }

  private static void registerInheritedTaskModelsInParentTaskModel(@NotNull GradleIdeaModelHolder models) {
    for (var buildModel : models.getAllBuilds()) {
      GradleTreeTraverserUtil.backwardTraverseTree(buildModel.getRootProject(), it -> it.getChildProjects(), projectModel -> {
        var taskModel = (DefaultGradleTaskModel)models.getProjectModel(projectModel, GradleTaskModel.class);
        if (taskModel == null) return;

        var tasks = new HashMap<>(taskModel.getTasks());

        for (var childProjectModel : projectModel.getChildProjects()) {
          var childTaskModel = (DefaultGradleTaskModel)models.getProjectModel(childProjectModel, GradleTaskModel.class);
          if (childTaskModel == null) continue;

          for (var childTask : childTaskModel.getTasks().values()) {
            if (tasks.containsKey(childTask.getName())) continue;

            var inheritedTask = new DefaultExternalTask(childTask);
            inheritedTask.setInherited(true);
            tasks.put(inheritedTask.getName(), inheritedTask);
          }
        }

        taskModel.setTasks(tasks);
      });
    }
  }

  private static void associateTaskModelsWithExternalProjects(@NotNull GradleIdeaModelHolder models) {
    for (var buildModel : models.getAllBuilds()) {
      for (var projectModel : buildModel.getProjects()) {
        var externalProject = (DefaultExternalProject)models.getProjectModel(projectModel, ExternalProject.class);
        var taskModel = (DefaultGradleTaskModel)models.getProjectModel(projectModel, GradleTaskModel.class);
        if (externalProject == null || taskModel == null) continue;

        externalProject.setTaskModel(taskModel);
      }
    }
  }

  private static void mergeSourceSetContentRoots(
    @NotNull ProjectResolverContext resolverContext,
    @NotNull Map<String, Pair<DataNode<ModuleData>, IdeaModule>> moduleMap
  ) {
    var modules = ContainerUtil.map2Map(moduleMap.values(), it -> new Pair<>(it.getSecond(), it.getFirst()));
    if (resolverContext.isResolveModulePerSourceSet()) {
      mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, modules);
    }
    else {
      mergeSourceSetContentRootsInModulePerProjectMode(resolverContext, modules);
    }
  }

  /**
   * Gradle source set haven't the content root term.
   * Therefore, IDEA needs to deduct the best content roots for the specified source set directories.
   * <p>
   * For example, it creates content roots src/main for the source set directories src/main/java and src/main/resources.
   * Same for src/test/java and src/test/resources, it creates src/test content root.
   */
  @VisibleForTesting
  @ApiStatus.Internal
  static void mergeSourceSetContentRootsInModulePerSourceSetMode(
    @NotNull ProjectResolverContext resolverContext,
    @NotNull Map<? extends ProjectModel, DataNode<ModuleData>> moduleMap
  ) {
    var contentRootIndex = new GradleContentRootIndex();

    for (var moduleEntry : moduleMap.entrySet()) {
      var moduleNode = moduleEntry.getValue();

      for (var sourceSetNode : findAll(moduleNode, GradleSourceSetData.KEY)) {
        contentRootIndex.addSourceRoots(sourceSetNode);
      }
    }

    for (var moduleEntry : moduleMap.entrySet()) {
      var projectModel = moduleEntry.getKey();
      var moduleNode = moduleEntry.getValue();

      var externalProject = resolverContext.getProjectModel(projectModel, ExternalProject.class);
      if (externalProject == null) continue;

      for (var sourceSetNode : findAll(moduleNode, GradleSourceSetData.KEY)) {
        var contentRootPaths = contentRootIndex.resolveContentRoots(externalProject, sourceSetNode);

        var contentRootNodes = CanonicalPathPrefixTreeFactory.INSTANCE.<ContentRootData>createMap();
        for (var contentRootPath : contentRootPaths) {
          var contentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, contentRootPath);
          contentRootNodes.set(contentRootPath, contentRootData);
        }

        for (var contentRootNode : findAll(sourceSetNode, ProjectKeys.CONTENT_ROOT)) {
          for (var sourceRootType : ExternalSystemSourceType.values()) {
            for (var sourceRoot : contentRootNode.getData().getPaths(sourceRootType)) {
              var sourceRootPath = sourceRoot.getPath();
              var packagePrefix = sourceRoot.getPackagePrefix();
              var contentRootData = ContainerUtil.getLastItem(contentRootNodes.getAncestorValues(sourceRootPath));
              contentRootData.storePath(sourceRootType, sourceRootPath, packagePrefix);
            }
          }
          contentRootNode.clear(true);
        }

        for (var contentRootData : contentRootNodes.values()) {
          sourceSetNode.createChild(ProjectKeys.CONTENT_ROOT, contentRootData);
        }
      }
    }
  }

  private static void mergeSourceSetContentRootsInModulePerProjectMode(
    @NotNull ProjectResolverContext resolverContext,
    @NotNull Map<? extends ProjectModel, DataNode<ModuleData>> moduleMap
  ) {
    for (var moduleEntry : moduleMap.entrySet()) {
      var projectModel = moduleEntry.getKey();
      var moduleNode = moduleEntry.getValue();

      var externalProject = resolverContext.getProjectModel(projectModel, ExternalProject.class);
      if (externalProject == null) continue;

      var projectRootPath = NioPathUtil.toCanonicalPath(externalProject.getProjectDir().toPath());

      var projectContentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, projectRootPath);
      var externalContentRootNodes = new ArrayList<ContentRootData>();

      for (var contentRootNode : findAll(moduleNode, ProjectKeys.CONTENT_ROOT)) {
        for (var sourceRootType : ExternalSystemSourceType.values()) {
          for (var sourceRoot : contentRootNode.getData().getPaths(sourceRootType)) {
            var sourceRootPath = sourceRoot.getPath();
            var packagePrefix = sourceRoot.getPackagePrefix();
            if (FileUtil.isAncestor(projectRootPath, sourceRootPath, false)) {
              projectContentRootData.storePath(sourceRootType, sourceRootPath, packagePrefix);
            }
            else {
              var externalContentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, sourceRootPath);
              externalContentRootData.storePath(sourceRootType, sourceRootPath, packagePrefix);
              externalContentRootNodes.add(externalContentRootData);
            }
          }
        }
        contentRootNode.clear(true);
      }

      moduleNode.createChild(ProjectKeys.CONTENT_ROOT, projectContentRootData);
      for (var externalContentRootData : externalContentRootNodes) {
        moduleNode.createChild(ProjectKeys.CONTENT_ROOT, externalContentRootData);
      }
    }
  }

  private final class ProjectConnectionDataNodeFunction implements Function<ProjectConnection, DataNode<ProjectData>> {
    private final @NotNull GradleProjectResolverExtension myProjectResolverChain;
    private final @NotNull DefaultProjectResolverContext myResolverContext;

    private ProjectConnectionDataNodeFunction(
      @NotNull DefaultProjectResolverContext resolverContext,
      @NotNull GradleProjectResolverExtension projectResolverChain
    ) {
      myResolverContext = resolverContext;
      myProjectResolverChain = projectResolverChain;
    }

    @Override
    public @NotNull DataNode<ProjectData> fun(@NotNull ProjectConnection connection) {
      ExternalSystemTaskId taskId = myResolverContext.getExternalSystemTaskId();
      final long activityId = taskId.getId();

      return computeCancellable(myResolverContext, () -> {
        try {
          myResolverContext.setConnection(connection);
          return doResolveProjectInfo(myResolverContext, myProjectResolverChain);
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

          throw myProjectResolverChain.getUserFriendlyError(
            myResolverContext.getBuildEnvironment(), e, myResolverContext.getProjectPath(), null);
        }
      });
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
    return createProjectResolverChain(null);
  }

  private static @NotNull GradleProjectResolverExtension createProjectResolverChain(
    @Nullable DefaultProjectResolverContext resolverContext
  ) {
    Predicate<GradleProjectResolverExtension> extensionsFilter =
      resolverContext == null ? __ -> true :
      resolverContext.getPolicy() == null ? __ -> true :
      resolverContext.getPolicy().getExtensionsFilter();
    Stream<GradleProjectResolverExtension> extensions = GradleProjectResolverUtil.createProjectResolvers(resolverContext)
      .filter(extensionsFilter.or(BaseResolverExtension.class::isInstance));

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
}
