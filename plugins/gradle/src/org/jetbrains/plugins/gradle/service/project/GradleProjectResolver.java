/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemDebugEnvironment;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.gradle.tooling.*;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.model.data.CompositeBuildData;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.remote.impl.GradleLibraryNamesMixer;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.execution.UnsupportedCancellationToken;
import org.jetbrains.plugins.gradle.settings.ClassHolder;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleBuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.*;

/**
 * @author Denis Zhdanov, Vladislav Soroka
 * @since 8/8/11 11:09 AM
 */
public class GradleProjectResolver implements ExternalSystemProjectResolver<GradleExecutionSettings> {

  private static final Logger LOG = Logger.getInstance(GradleProjectResolver.class);

  @NotNull private final GradleExecutionHelper myHelper;
  private final GradleLibraryNamesMixer myLibraryNamesMixer = new GradleLibraryNamesMixer();

  private final MultiMap<ExternalSystemTaskId, CancellationTokenSource> myCancellationMap = MultiMap.create();
  public static final Key<Map<String/* module id */, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>>> RESOLVED_SOURCE_SETS =
    Key.create("resolvedSourceSets");
  public static final Key<Map<String/* output path */, Pair<String /* module id*/, ExternalSystemSourceType>>> MODULES_OUTPUTS =
    Key.create("moduleOutputsMap");
  public static final Key<MultiMap<ExternalSystemSourceType, String /* output path*/>> GRADLE_OUTPUTS = Key.create("gradleOutputs");
  public static final Key<Map<String/* artifact path */, String /* module id*/>> CONFIGURATION_ARTIFACTS =
    Key.create("gradleArtifactsMap");

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
  public DataNode<ProjectData> resolveProjectInfo(@NotNull final ExternalSystemTaskId id,
                                                  @NotNull final String projectPath,
                                                  final boolean isPreviewMode,
                                                  @Nullable final GradleExecutionSettings settings,
                                                  @NotNull final ExternalSystemTaskNotificationListener listener)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException {

    if (isPreviewMode) {
      // Create project preview model w/o request to gradle, there are two main reasons for the it:
      // * Slow project open - even the simplest project info provided by gradle can be gathered too long (mostly because of new gradle distribution download and downloading buildscript dependencies)
      // * Ability to open  an invalid projects (e.g. with errors in build scripts)
      String projectName = new File(projectPath).getName();
      ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectName, projectPath, projectPath);
      DataNode<ProjectData> projectDataNode = new DataNode<>(ProjectKeys.PROJECT, projectData, null);

      final String ideProjectPath = settings == null ? null : settings.getIdeProjectPath();
      final String mainModuleFileDirectoryPath = ideProjectPath == null ? projectPath : ideProjectPath;

      projectDataNode
        .createChild(ProjectKeys.MODULE, new ModuleData(projectName, GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(),
                                                                     projectName, mainModuleFileDirectoryPath, projectPath))
        .createChild(ProjectKeys.CONTENT_ROOT, new ContentRootData(GradleConstants.SYSTEM_ID, projectPath));
      return projectDataNode;
    }

    if (settings != null) {
      myHelper.ensureInstalledWrapper(id, projectPath, settings, listener);
    }

    final GradleProjectResolverExtension projectResolverChain = createProjectResolverChain(settings);
    DefaultProjectResolverContext resolverContext = new DefaultProjectResolverContext(id, projectPath, settings, listener, false);
    final DataNode<ProjectData> resultProjectDataNode = myHelper.execute(
      projectPath, settings, new ProjectConnectionDataNodeFunction(resolverContext, projectResolverChain, false)
    );

    // auto-discover buildSrc project if needed
    File gradleUserHome = resolverContext.getUserData(GRADLE_HOME_DIR);
    String gradleHome = gradleUserHome == null ? null : gradleUserHome.getPath();
    GradleExecutionSettings buildSrcProjectSettings;
    if (gradleHome != null) {
      if (settings != null) {
        buildSrcProjectSettings = new GradleExecutionSettings(gradleHome,
                                                              settings.getServiceDirectory(),
                                                              DistributionType.LOCAL,
                                                              settings.isOfflineWork());
        buildSrcProjectSettings.setIdeProjectPath(settings.getIdeProjectPath());
        buildSrcProjectSettings.setJavaHome(settings.getJavaHome());
        buildSrcProjectSettings.setResolveModulePerSourceSet(settings.isResolveModulePerSourceSet());
        buildSrcProjectSettings.setRemoteProcessIdleTtlInMs(settings.getRemoteProcessIdleTtlInMs());
        buildSrcProjectSettings.setVerboseProcessing(settings.isVerboseProcessing());
        buildSrcProjectSettings.setWrapperPropertyFile(settings.getWrapperPropertyFile());
        buildSrcProjectSettings.withArguments(settings.getArguments())
          .withEnvironmentVariables(settings.getEnv())
          .passParentEnvs(settings.isPassParentEnvs())
          .withVmOptions(settings.getVmOptions());
      }
      else {
        buildSrcProjectSettings = new GradleExecutionSettings(gradleHome, null, DistributionType.LOCAL, false);
      }
    }
    else {
      buildSrcProjectSettings = settings;
    }

    final String buildSrcProjectPath = projectPath + "/buildSrc";
    DefaultProjectResolverContext buildSrcResolverCtx =
      new DefaultProjectResolverContext(id, buildSrcProjectPath, buildSrcProjectSettings, listener, false);
    resolverContext.copyUserDataTo(buildSrcResolverCtx);
    handleBuildSrcProject(resultProjectDataNode, new ProjectConnectionDataNodeFunction(buildSrcResolverCtx, projectResolverChain, true));
    return resultProjectDataNode;
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener) {
    synchronized (myCancellationMap) {
      for (CancellationTokenSource cancellationTokenSource : myCancellationMap.get(id)) {
        cancellationTokenSource.cancel();
      }
    }
    return true;
  }

  @NotNull
  private DataNode<ProjectData> doResolveProjectInfo(@NotNull final DefaultProjectResolverContext resolverCtx,
                                                     @NotNull final GradleProjectResolverExtension projectResolverChain,
                                                     boolean isBuildSrcProject)
    throws IllegalArgumentException, IllegalStateException {

    final BuildEnvironment buildEnvironment = GradleExecutionHelper.getBuildEnvironment(resolverCtx);
    GradleVersion gradleVersion = null;

    boolean isGradleProjectDirSupported = false;
    boolean isCompositeBuildsSupported = false;
    if (buildEnvironment != null) {
      gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
      isGradleProjectDirSupported = gradleVersion.compareTo(GradleVersion.version("2.4")) >= 0;
      isCompositeBuildsSupported = isGradleProjectDirSupported && gradleVersion.compareTo(GradleVersion.version("3.1")) >= 0;
    }
    final ProjectImportAction projectImportAction =
      new ProjectImportAction(resolverCtx.isPreviewMode(), isGradleProjectDirSupported, isCompositeBuildsSupported);

    GradleExecutionSettings executionSettings = resolverCtx.getSettings();
    if (executionSettings == null) {
      executionSettings = new GradleExecutionSettings(null, null, DistributionType.BUNDLED, false);
    }

    executionSettings.withArgument("-Didea.version=" + getIdeaVersion());
    if(resolverCtx.isPreviewMode()){
      executionSettings.withArgument("-Didea.isPreviewMode=true");
      final Set<Class> previewLightWeightToolingModels = ContainerUtil.set(ExternalProjectPreview.class, GradleBuild.class);
      projectImportAction.addExtraProjectModelClasses(previewLightWeightToolingModels);
    }
    if(resolverCtx.isResolveModulePerSourceSet()) {
      executionSettings.withArgument("-Didea.resolveSourceSetDependencies=true");
    }

    if (!isBuildSrcProject) {
      for (GradleBuildParticipant buildParticipant : executionSettings.getExecutionWorkspace().getBuildParticipants()) {
        executionSettings.withArguments(GradleConstants.INCLUDE_BUILD_CMD_OPTION, buildParticipant.getProjectPath());
      }
    }

    final Set<Class> toolingExtensionClasses = ContainerUtil.newHashSet();
    final GradleImportCustomizer importCustomizer = GradleImportCustomizer.get();
    for (GradleProjectResolverExtension resolverExtension = projectResolverChain;
         resolverExtension != null;
         resolverExtension = resolverExtension.getNext()) {
      // inject ProjectResolverContext into gradle project resolver extensions
      resolverExtension.setProjectResolverContext(resolverCtx);
      // pre-import checks
      resolverExtension.preImportCheck();

      if(!resolverCtx.isPreviewMode()){
        // register classes of extra gradle project models required for extensions (e.g. com.android.builder.model.AndroidProject)
        projectImportAction.addExtraProjectModelClasses(resolverExtension.getExtraProjectModelClasses());
      }

      if (importCustomizer == null || importCustomizer.useExtraJvmArgs()) {
        // collect extra JVM arguments provided by gradle project resolver extensions
        final ParametersList parametersList = new ParametersList();
        for (Pair<String, String> jvmArg : resolverExtension.getExtraJvmArgs()) {
          parametersList.addProperty(jvmArg.first, jvmArg.second);
        }
        executionSettings.withVmOptions(parametersList.getParameters());
      }
      // collect extra command-line arguments
      executionSettings.withArguments(resolverExtension.getExtraCommandLineArgs());
      // collect tooling extensions classes
      toolingExtensionClasses.addAll(resolverExtension.getToolingExtensionsClasses());
    }

    BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor = resolverCtx.getConnection().action(projectImportAction);

    File initScript = GradleExecutionHelper.generateInitScript(isBuildSrcProject, toolingExtensionClasses);
    if (initScript != null) {
      executionSettings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.getAbsolutePath());
    }

    GradleExecutionHelper.prepare(buildActionExecutor, resolverCtx.getExternalSystemTaskId(),
                                  executionSettings, resolverCtx.getListener(), resolverCtx.getConnection());
    resolverCtx.checkCancelled();

    ProjectImportAction.AllModels allModels;
    final CancellationTokenSource cancellationTokenSource = GradleConnector.newCancellationTokenSource();
    final long startTime = System.currentTimeMillis();
    try {
      resolverCtx.setCancellationTokenSource(cancellationTokenSource);
      buildActionExecutor.withCancellationToken(cancellationTokenSource.token());
      synchronized (myCancellationMap) {
        myCancellationMap.putValue(resolverCtx.getExternalSystemTaskId(), cancellationTokenSource);
        if (gradleVersion != null && gradleVersion.compareTo(GradleVersion.version("2.1")) < 0) {
          myCancellationMap.putValue(resolverCtx.getExternalSystemTaskId(), new UnsupportedCancellationToken());
        }
      }
      allModels = buildActionExecutor.run();
      if (allModels == null) {
        throw new IllegalStateException("Unable to get project model for the project: " + resolverCtx.getProjectPath());
      }
    }
    catch (UnsupportedVersionException unsupportedVersionException) {
      resolverCtx.checkCancelled();

      // Old gradle distribution version used (before ver. 1.8)
      // fallback to use ModelBuilder gradle tooling API
      Class<? extends IdeaProject> aClass = resolverCtx.isPreviewMode() ? BasicIdeaProject.class : IdeaProject.class;
      ModelBuilder<? extends IdeaProject> modelBuilder = myHelper.getModelBuilder(
        aClass,
        resolverCtx.getExternalSystemTaskId(),
        executionSettings,
        resolverCtx.getConnection(),
        resolverCtx.getListener());

      final IdeaProject ideaProject = modelBuilder.get();
      allModels = new ProjectImportAction.AllModels(ideaProject);
    }
    finally {
      final long timeInMs = (System.currentTimeMillis() - startTime);
      synchronized (myCancellationMap) {
        myCancellationMap.remove(resolverCtx.getExternalSystemTaskId(), cancellationTokenSource);
      }
      LOG.debug(String.format("Gradle data obtained in %d ms", timeInMs));
    }

    resolverCtx.checkCancelled();

    allModels.setBuildEnvironment(buildEnvironment);

    final long startDataConversionTime = System.currentTimeMillis();
    extractExternalProjectModels(allModels, resolverCtx);

    // import project data
    ProjectData projectData = projectResolverChain.createProject();
    DataNode<ProjectData> projectDataNode = new DataNode<>(ProjectKeys.PROJECT, projectData, null);

    // import java project data
    JavaProjectData javaProjectData = projectResolverChain.createJavaProjectData();
    projectDataNode.createChild(JavaProjectData.KEY, javaProjectData);

    IdeaProject ideaProject = resolverCtx.getModels().getIdeaProject();

    projectResolverChain.populateProjectExtraModels(ideaProject, projectDataNode);

    DomainObjectSet<? extends IdeaModule> gradleModules = ideaProject.getModules();
    if (gradleModules == null || gradleModules.isEmpty()) {
      throw new IllegalStateException("No modules found for the target project: " + ideaProject);
    }

    Collection<IdeaModule> includedModules = exposeCompositeBuild(allModels, resolverCtx, projectDataNode);
    final Map<String /* module id */, Pair<DataNode<ModuleData>, IdeaModule>> moduleMap = ContainerUtilRt.newHashMap();
    final Map<String /* module id */, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetsMap = ContainerUtil.newHashMap();
    projectDataNode.putUserData(RESOLVED_SOURCE_SETS, sourceSetsMap);

    final Map<String/* output path */, Pair<String /* module id*/, ExternalSystemSourceType>> moduleOutputsMap =
      ContainerUtil.newTroveMap(FileUtil.PATH_HASHING_STRATEGY);
    projectDataNode.putUserData(MODULES_OUTPUTS, moduleOutputsMap);
    final Map<String/* artifact path */, String /* module id*/> artifactsMap =
      ContainerUtil.newTroveMap(FileUtil.PATH_HASHING_STRATEGY);
    projectDataNode.putUserData(CONFIGURATION_ARTIFACTS, artifactsMap);

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

      DataNode<ModuleData> moduleDataNode = projectResolverChain.createModule(gradleModule, projectDataNode);
      String mainModuleId = getModuleId(resolverCtx, gradleModule);

      if (moduleMap.containsKey(mainModuleId)) {
        // we should ensure deduplicated module names in the scope of single import
        throw new IllegalStateException("Duplicate modules names detected: " + gradleModule);
      }
      moduleMap.put(mainModuleId, Pair.create(moduleDataNode, gradleModule));
    }

    executionSettings.getExecutionWorkspace().addModuleMap(moduleMap);

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

      projectResolverChain.populateModuleContentRoots(ideaModule, moduleDataNode);
      projectResolverChain.populateModuleCompileOutputSettings(ideaModule, moduleDataNode);
      if (!isBuildSrcProject) {
        projectResolverChain.populateModuleTasks(ideaModule, moduleDataNode, projectDataNode);
      }

      final List<DataNode<? extends ModuleData>> modules = ContainerUtil.newSmartList();
      modules.add(moduleDataNode);
      modules.addAll(ExternalSystemApiUtil.findAll(moduleDataNode, GradleSourceSetData.KEY));

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
            artifactsMap.put(ExternalSystemApiUtil.toCanonicalPath(artifactFile.getAbsolutePath()), moduleData.getId());
          }
        }
      }
    }
    resolverCtx.putUserData(GRADLE_HOME_DIR, gradleHomeDir);

    for (final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values()) {
      final DataNode<ModuleData> moduleDataNode = pair.first;
      final IdeaModule ideaModule = pair.second;
      projectResolverChain.populateModuleDependencies(ideaModule, moduleDataNode, projectDataNode);
      projectResolverChain.populateModuleExtraModels(ideaModule, moduleDataNode);
    }
    mergeSourceSetContentRoots(moduleMap, resolverCtx);
    if(resolverCtx.isResolveModulePerSourceSet()) {
      mergeLibraryAndModuleDependencyData(projectDataNode, resolverCtx.getGradleUserHome(), gradleHomeDir, gradleVersion);
    }
    projectDataNode.putUserData(RESOLVED_SOURCE_SETS, null);
    projectDataNode.putUserData(MODULES_OUTPUTS, null);
    projectDataNode.putUserData(CONFIGURATION_ARTIFACTS, null);

    // ensure unique library names
    Collection<DataNode<LibraryData>> libraries = ExternalSystemApiUtil.getChildren(projectDataNode, ProjectKeys.LIBRARY);
    myLibraryNamesMixer.mixNames(libraries);

    final long timeConversionInMs = (System.currentTimeMillis() - startDataConversionTime);
    LOG.debug(String.format("Project data resolved in %d ms", timeConversionInMs));
    return projectDataNode;
  }

  @NotNull
  private static Collection<IdeaModule> exposeCompositeBuild(ProjectImportAction.AllModels allModels,
                                                             DefaultProjectResolverContext resolverCtx,
                                                             DataNode<ProjectData> projectDataNode) {
    if(resolverCtx.getSettings() != null && !resolverCtx.getSettings().getExecutionWorkspace().getBuildParticipants().isEmpty()) {
      return Collections.emptyList();
    }
    CompositeBuildData compositeBuildData;
    List<IdeaModule> gradleIncludedModules = new SmartList<>();
    List<IdeaProject> includedBuilds = allModels.getIncludedBuilds();
    if (!includedBuilds.isEmpty()) {
      ProjectData projectData = projectDataNode.getData();
      compositeBuildData = new CompositeBuildData(projectData.getLinkedExternalProjectPath());
      for (IdeaProject project : includedBuilds) {
        if (!project.getModules().isEmpty()) {
          String rootProjectName = project.getName();
          BuildParticipant buildParticipant = new BuildParticipant();
          gradleIncludedModules.addAll(project.getModules());
          GradleProject gradleProject = project.getModules().getAt(0).getGradleProject();
          String projectPath = null;
          do {
            try {
              projectPath = ExternalSystemApiUtil.toCanonicalPath(gradleProject.getProjectDirectory().getCanonicalPath());
            }
            catch (IOException e) {
              LOG.warn("construction of the canonical path for the module fails", e);
            }
          }
          while ((gradleProject = gradleProject.getParent()) != null);
          if (projectPath != null) {
            buildParticipant.setRootProjectName(rootProjectName);
            buildParticipant.setRootPath(projectPath);
            for (IdeaModule module : project.getModules()) {
              try {
                String modulePath =
                  ExternalSystemApiUtil.toCanonicalPath(module.getGradleProject().getProjectDirectory().getCanonicalPath());
                buildParticipant.getProjects().add(modulePath);
              }
              catch (IOException e) {
                LOG.warn("construction of the canonical path for the module fails", e);
              }
            }

            compositeBuildData.getCompositeParticipants().add(buildParticipant);
          }
        }
      }
      projectDataNode.createChild(CompositeBuildData.KEY, compositeBuildData);
    }
    return gradleIncludedModules;
  }

  private static void mergeLibraryAndModuleDependencyData(DataNode<ProjectData> projectDataNode,
                                                          @NotNull File gradleUserHomeDir,
                                                          @Nullable File gradleHomeDir,
                                                          @Nullable GradleVersion gradleVersion) {
    final Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap =
      projectDataNode.getUserData(RESOLVED_SOURCE_SETS);
    assert sourceSetMap != null;

    final Map<String, Pair<String, ExternalSystemSourceType>> moduleOutputsMap =
      projectDataNode.getUserData(MODULES_OUTPUTS);
    assert moduleOutputsMap != null;

    final Map<String, String> artifactsMap = projectDataNode.getUserData(CONFIGURATION_ARTIFACTS);
    assert artifactsMap != null;

    final Collection<DataNode<LibraryDependencyData>> libraryDependencies =
      ExternalSystemApiUtil.findAllRecursively(projectDataNode, ProjectKeys.LIBRARY_DEPENDENCY);

    for (DataNode<LibraryDependencyData> libraryDependencyDataNode : libraryDependencies) {
      final DataNode<?> libraryNodeParent = libraryDependencyDataNode.getParent();
      if (libraryNodeParent == null) continue;

      final LibraryDependencyData libraryDependencyData = libraryDependencyDataNode.getData();
      final LibraryData libraryData = libraryDependencyData.getTarget();
      final Set<String> libraryPaths = libraryData.getPaths(LibraryPathType.BINARY);
      if (libraryPaths.isEmpty()) continue;
      if (StringUtil.isNotEmpty(libraryData.getExternalName())) {
        attachSourcesAndJavadocFromGradleCacheIfNeeded(gradleUserHomeDir, libraryData);
        continue;
      }

      boolean projectDependencyCandidate = libraryPaths.size() == 1 && !libraryDependencyDataNode.getChildren().isEmpty();

      final LinkedList<String> unprocessedPaths = ContainerUtil.newLinkedList(libraryPaths);
      while (!unprocessedPaths.isEmpty()) {
        final String path = unprocessedPaths.remove();

        Set<String> targetModuleOutputPaths = null;

        final String moduleId;
        final Pair<String, ExternalSystemSourceType> sourceTypePair = moduleOutputsMap.get(path);
        if (sourceTypePair == null) {
          moduleId = artifactsMap.get(path);
          if (moduleId != null) {
            targetModuleOutputPaths = ContainerUtil.set(path);
          }
        }
        else {
          moduleId = sourceTypePair.first;
        }
        if (moduleId == null) continue;

        final Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> pair = sourceSetMap.get(moduleId);
        if (pair == null) {
          continue;
        }

        final ModuleData moduleData = pair.first.getData();
        if (targetModuleOutputPaths == null) {
          final Set<String> compileSet = ContainerUtil.newHashSet();
          MultiMap<ExternalSystemSourceType, String> gradleOutputs = pair.first.getUserData(GRADLE_OUTPUTS);
          if(gradleOutputs != null) {
            ContainerUtil.addAllNotNull(compileSet,
                                        gradleOutputs.get(ExternalSystemSourceType.SOURCE));
            ContainerUtil.addAllNotNull(compileSet,
                                        gradleOutputs.get(ExternalSystemSourceType.RESOURCE));
          }
          if (!compileSet.isEmpty() && ContainerUtil.intersects(libraryPaths, compileSet)) {
            targetModuleOutputPaths = compileSet;
          }
          else {
            final Set<String> testSet = ContainerUtil.newHashSet();
            if(gradleOutputs != null) {
              ContainerUtil.addAllNotNull(testSet,
                                          gradleOutputs.get(ExternalSystemSourceType.TEST));
              ContainerUtil.addAllNotNull(testSet,
                                          gradleOutputs.get(ExternalSystemSourceType.TEST_RESOURCE));
            }
            if (!testSet.isEmpty() && ContainerUtil.intersects(libraryPaths, testSet)) {
              targetModuleOutputPaths = testSet;
            }
          }
        }

        final ModuleData ownerModule = libraryDependencyData.getOwnerModule();
        final ModuleDependencyData moduleDependencyData = new ModuleDependencyData(ownerModule, moduleData);
        moduleDependencyData.setScope(libraryDependencyData.getScope());
        if ("test".equals(pair.second.getName())) {
          moduleDependencyData.setProductionOnTestDependency(true);
        }
        final DataNode<ModuleDependencyData> found = ExternalSystemApiUtil.find(
          libraryNodeParent, ProjectKeys.MODULE_DEPENDENCY, node -> {
            if (moduleDependencyData.getInternalName().equals(node.getData().getInternalName())) {
              moduleDependencyData.setModuleDependencyArtifacts(node.getData().getModuleDependencyArtifacts());
            }

            final boolean result;
            // ignore provided scope during the search since it can be resolved incorrectly for file dependencies on a source set outputs
            if(moduleDependencyData.getScope() == DependencyScope.PROVIDED) {
              moduleDependencyData.setScope(node.getData().getScope());
              result = moduleDependencyData.equals(node.getData());
              moduleDependencyData.setScope(DependencyScope.PROVIDED);
            } else {
              result = moduleDependencyData.equals(node.getData());
            }
            return result;
          });

        if (targetModuleOutputPaths != null) {
          if (found == null) {
            DataNode<ModuleDependencyData> moduleDependencyNode = libraryNodeParent.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData);
            if(projectDependencyCandidate) {
              for (DataNode<?> node : libraryDependencyDataNode.getChildren()) {
                moduleDependencyNode.addChild(node);
              }
            }
          }
          libraryPaths.removeAll(targetModuleOutputPaths);
          unprocessedPaths.removeAll(targetModuleOutputPaths);
          if (libraryPaths.isEmpty()) {
            libraryDependencyDataNode.clear(true);
            break;
          }
          continue;
        }
        else {
          // do not add the path as library dependency if another module dependency is already contain the path as one of its output paths
          if (found != null) {
            libraryPaths.remove(path);
            if (libraryPaths.isEmpty()) {
              libraryDependencyDataNode.clear(true);
              break;
            }
            continue;
          }
        }

        final ExternalSourceDirectorySet directorySet = pair.second.getSources().get(sourceTypePair.second);
        if (directorySet != null) {
          for (File file : directorySet.getSrcDirs()) {
            libraryData.addPath(LibraryPathType.SOURCE, file.getAbsolutePath());
          }
        }
      }

      if (libraryDependencyDataNode.getParent() != null) {
        if (libraryPaths.size() > 1) {
          List<String> toRemove = ContainerUtil.newSmartList();
          for (String path : libraryPaths) {
            final File binaryPath = new File(path);
            if (binaryPath.isFile()) {
              final LibraryData extractedLibrary = new LibraryData(libraryDependencyData.getOwner(), "");
              extractedLibrary.addPath(LibraryPathType.BINARY, path);
              if (gradleHomeDir != null && gradleVersion != null) {
                attachGradleSdkSources(binaryPath, extractedLibrary, gradleHomeDir, gradleVersion);
              }
              LibraryDependencyData extractedDependencyData = new LibraryDependencyData(
                libraryDependencyData.getOwnerModule(), extractedLibrary, LibraryLevel.MODULE);
              libraryDependencyDataNode.getParent().createChild(ProjectKeys.LIBRARY_DEPENDENCY, extractedDependencyData);

              toRemove.add(path);
            }
          }
          libraryPaths.removeAll(toRemove);
          if (libraryPaths.isEmpty()) {
            libraryDependencyDataNode.clear(true);
          }
        }
      }
    }
  }

  private static void extractExternalProjectModels(@NotNull ProjectImportAction.AllModels models,
                                                   @NotNull ProjectResolverContext resolverCtx) {
    resolverCtx.setModels(models);
    final Class<? extends ExternalProject> modelClazz = resolverCtx.isPreviewMode() ? ExternalProjectPreview.class : ExternalProject.class;
    final ExternalProject externalRootProject = models.getExtraProject((IdeaModule)null, modelClazz);
    if (externalRootProject == null) return;

    final DefaultExternalProject wrappedExternalRootProject = new DefaultExternalProject(externalRootProject);
    models.addExtraProject(wrappedExternalRootProject, ExternalProject.class);
    final Map<String, ExternalProject> externalProjectsMap = createExternalProjectsMap(null, wrappedExternalRootProject);

    DomainObjectSet<? extends IdeaModule> gradleModules = models.getIdeaProject().getModules();
    if (gradleModules != null && !gradleModules.isEmpty()) {
      for (IdeaModule ideaModule : gradleModules) {
        final ExternalProject externalProject = externalProjectsMap.get(getModuleId(resolverCtx, ideaModule));
        if (externalProject != null) {
          models.addExtraProject(externalProject, ExternalProject.class, ideaModule.getGradleProject());
        }
      }
    }
    for (IdeaProject project : models.getIncludedBuilds()) {
      DomainObjectSet<? extends IdeaModule> ideaModules = project.getModules();
      if (ideaModules.isEmpty()) continue;

      GradleProject gradleProject = ideaModules.getAt(0).getGradleProject();
      while (gradleProject.getParent() != null) {
        gradleProject = gradleProject.getParent();
      }
      final ExternalProject externalIncludedRootProject = models.getExtraProject(gradleProject, modelClazz);
      if (externalIncludedRootProject == null) continue;
      final DefaultExternalProject wrappedExternalIncludedRootProject = new DefaultExternalProject(externalIncludedRootProject);
      wrappedExternalRootProject.getChildProjects().put(wrappedExternalIncludedRootProject.getName(), wrappedExternalIncludedRootProject);
      String compositePrefix = project.getName();
      final Map<String, ExternalProject> externalIncludedProjectsMap =
        createExternalProjectsMap(compositePrefix, wrappedExternalIncludedRootProject);
      for (IdeaModule ideaModule : ideaModules) {
        final ExternalProject externalProject = externalIncludedProjectsMap.get(getModuleId(resolverCtx, ideaModule));
        if (externalProject != null) {
          models.addExtraProject(externalProject, ExternalProject.class, ideaModule.getGradleProject());
        }
      }
    }
  }

  private static Map<String, ExternalProject> createExternalProjectsMap(@Nullable String compositePrefix,
                                                                        @Nullable final ExternalProject rootExternalProject) {
    final Map<String, ExternalProject> externalProjectMap = ContainerUtilRt.newHashMap();

    if (rootExternalProject == null) return externalProjectMap;

    Queue<ExternalProject> queue = new LinkedList<>();
    queue.add(rootExternalProject);

    while (!queue.isEmpty()) {
      ExternalProject externalProject = queue.remove();
      queue.addAll(externalProject.getChildProjects().values());
      final String moduleName = externalProject.getName();
      final String qName = externalProject.getQName();
      String moduleId = StringUtil.isEmpty(qName) || ":".equals(qName) ? moduleName : qName;
      if (compositePrefix != null && externalProject != rootExternalProject) {
        moduleId = compositePrefix + moduleId;
      }
      externalProjectMap.put(moduleId, externalProject);
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
    final Factory<Counter> counterFactory = () -> new Counter();

    final Map<String, Counter> weightMap = ContainerUtil.newHashMap();
    for (final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values()) {
      final DataNode<ModuleData> moduleNode = pair.first;
      for (DataNode<ContentRootData> contentRootNode : ExternalSystemApiUtil.findAll(moduleNode, ProjectKeys.CONTENT_ROOT)) {
        File file = new File(contentRootNode.getData().getRootPath());
        while (file != null) {
          ContainerUtil.getOrCreate(weightMap, file.getPath(), counterFactory).increment();
          file = file.getParentFile();
        }
      }

      for (DataNode<GradleSourceSetData> sourceSetNode : ExternalSystemApiUtil.findAll(moduleNode, GradleSourceSetData.KEY)) {
        final Set<String> set = ContainerUtil.newHashSet();
        for (DataNode<ContentRootData> contentRootNode : ExternalSystemApiUtil.findAll(sourceSetNode, ProjectKeys.CONTENT_ROOT)) {
          File file = new File(contentRootNode.getData().getRootPath());
          while (file != null) {
            set.add(file.getPath());
            file = file.getParentFile();
          }
        }
        for (String path : set) {
          ContainerUtil.getOrCreate(weightMap, path, counterFactory).increment();
        }
      }
    }
    for (final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values()) {
      final DataNode<ModuleData> moduleNode = pair.first;
      final ExternalProject externalProject = resolverCtx.getExtraProject(pair.second, ExternalProject.class);
      if (externalProject == null) continue;

      if (resolverCtx.isResolveModulePerSourceSet()) {
        for (DataNode<GradleSourceSetData> sourceSetNode : ExternalSystemApiUtil.findAll(moduleNode, GradleSourceSetData.KEY)) {
          mergeModuleContentRoots(weightMap, externalProject, sourceSetNode);
        }
      }
      else {
        mergeModuleContentRoots(weightMap, externalProject, moduleNode);
      }
    }
  }

  private static void mergeModuleContentRoots(@NotNull Map<String, Counter> weightMap,
                                              @NotNull ExternalProject externalProject,
                                              @NotNull DataNode<? extends ModuleData> moduleNode) {
    final File buildDir = externalProject.getBuildDir();
    final MultiMap<String, ContentRootData> sourceSetRoots = MultiMap.create();
    Collection<DataNode<ContentRootData>> contentRootNodes = ExternalSystemApiUtil.findAll(moduleNode, ProjectKeys.CONTENT_ROOT);
    if(contentRootNodes.size() <= 1) return;

    for (DataNode<ContentRootData> contentRootNode : contentRootNodes) {
      File root = new File(contentRootNode.getData().getRootPath());
      if (FileUtil.isAncestor(buildDir, root, true)) continue;

      while (weightMap.containsKey(root.getParent()) && weightMap.get(root.getParent()).count <= 1) {
        root = root.getParentFile();
      }

      ContentRootData mergedContentRoot = null;
      String rootPath = ExternalSystemApiUtil.toCanonicalPath(root.getAbsolutePath());
      Set<String> paths = ContainerUtil.newHashSet(sourceSetRoots.keySet());
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
        if(sourceSetRoots.size() == 1) break;
      }

      if (mergedContentRoot == null) {
        mergedContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, root.getAbsolutePath());
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

  private void handleBuildSrcProject(@NotNull final DataNode<ProjectData> resultProjectDataNode,
                                     @NotNull final ProjectConnectionDataNodeFunction projectConnectionDataNodeFunction) {

    final String projectPath = projectConnectionDataNodeFunction.myResolverContext.getProjectPath();
    File projectPathFile = new File(projectPath);
    if (!projectPathFile.isDirectory()) {
      return;
    }

    if (ArrayUtil.isEmpty(projectPathFile.list((dir, name) -> !name.equals(".gradle") && !name.equals("build")))) {
      return;
    }

    if (projectConnectionDataNodeFunction.myResolverContext.isPreviewMode()) {
      ModuleData buildSrcModuleData =
        new ModuleData(":buildSrc", GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), "buildSrc", projectPath, projectPath);
      resultProjectDataNode.createChild(ProjectKeys.MODULE, buildSrcModuleData);
      return;
    }

    Set<String> paths = ContainerUtil.newHashSet();
    for (DataNode<ModuleData> moduleDataNode : ExternalSystemApiUtil.findAll(resultProjectDataNode, ProjectKeys.MODULE)) {
      String path = moduleDataNode.getData().getLinkedExternalProjectPath();
      if (path.equals(projectPath)) {
        // check if buildSrc project was already exposed in settings.gradle file
        return;
      }
      paths.add(path);
    }

    final DataNode<ProjectData> buildSrcProjectDataDataNode = myHelper.execute(
      projectPath, projectConnectionDataNodeFunction.myResolverContext.getSettings(), projectConnectionDataNodeFunction);

    if (buildSrcProjectDataDataNode != null) {
      for (DataNode<ModuleData> moduleNode : ExternalSystemApiUtil.getChildren(buildSrcProjectDataDataNode, ProjectKeys.MODULE)) {
        if (paths.contains(moduleNode.getData().getLinkedExternalProjectPath())) continue;

        resultProjectDataNode.addChild(moduleNode);
        if (!projectConnectionDataNodeFunction.myResolverContext.isUseQualifiedModuleNames()) {
          // adjust ide module group
          final ModuleData moduleData = moduleNode.getData();
          if (moduleData.getIdeModuleGroup() != null) {
            String[] moduleGroup = ArrayUtil.prepend(resultProjectDataNode.getData().getInternalName(), moduleData.getIdeModuleGroup());
            moduleData.setIdeModuleGroup(moduleGroup);

            for (DataNode<GradleSourceSetData> sourceSetNode : ExternalSystemApiUtil.getChildren(moduleNode, GradleSourceSetData.KEY)) {
              sourceSetNode.getData().setIdeModuleGroup(moduleGroup);
            }
          }
        }
      }
    }
  }

  private class ProjectConnectionDataNodeFunction implements Function<ProjectConnection, DataNode<ProjectData>> {
    @NotNull private final GradleProjectResolverExtension myProjectResolverChain;
    private final boolean myIsBuildSrcProject;
    private DefaultProjectResolverContext myResolverContext;

    private ProjectConnectionDataNodeFunction(@NotNull DefaultProjectResolverContext resolverContext,
                                              @NotNull GradleProjectResolverExtension projectResolverChain, boolean isBuildSrcProject) {
      myResolverContext = resolverContext;
      myProjectResolverChain = projectResolverChain;
      myIsBuildSrcProject = isBuildSrcProject;
    }

    @Override
    public DataNode<ProjectData> fun(ProjectConnection connection) {
      try {
        myResolverContext.setConnection(connection);
        return doResolveProjectInfo(myResolverContext, myProjectResolverChain, myIsBuildSrcProject);
      }
      catch (RuntimeException e) {
        LOG.info("Gradle project resolve error", e);
        throw myProjectResolverChain.getUserFriendlyError(e, myResolverContext.getProjectPath(), null);
      }
    }
  }

  @NotNull
  public static GradleProjectResolverExtension createProjectResolverChain(@Nullable final GradleExecutionSettings settings) {
    GradleProjectResolverExtension projectResolverChain;
    if (settings != null) {
      List<ClassHolder<? extends GradleProjectResolverExtension>> extensionClasses = settings.getResolverExtensions();
      if(extensionClasses.isEmpty()) {
        extensionClasses.add(ClassHolder.from(BaseGradleProjectResolverExtension.class));
      }
      Deque<GradleProjectResolverExtension> extensions = new ArrayDeque<>();
      for (ClassHolder<? extends GradleProjectResolverExtension> holder : extensionClasses) {
        final GradleProjectResolverExtension extension;
        try {
          extension = holder.getTargetClass().newInstance();
        }
        catch (Throwable e) {
          throw new IllegalArgumentException(
            String.format("Can't instantiate project resolve extension for class '%s'", holder.getTargetClassName()), e);
        }
        final GradleProjectResolverExtension previous = extensions.peekLast();
        if (previous != null) {
          previous.setNext(extension);
          if (previous.getNext() != extension) {
            throw new AssertionError("Illegal next resolver got, current resolver class is " + previous.getClass().getName());
          }
        }
        extensions.add(extension);
      }
      projectResolverChain = extensions.peekFirst();

      GradleProjectResolverExtension resolverExtension = projectResolverChain;
      assert resolverExtension != null;
      while (resolverExtension.getNext() != null) {
        resolverExtension = resolverExtension.getNext();
      }
      if (!(resolverExtension instanceof BaseGradleProjectResolverExtension)) {
        throw new AssertionError("Illegal last resolver got of class " + resolverExtension.getClass().getName());
      }
    }
    else {
      projectResolverChain = new BaseGradleProjectResolverExtension();
    }

    return projectResolverChain;
  }

  private static String getIdeaVersion() {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    return appInfo.getMajorVersion() + "." + appInfo.getMinorVersion();
  }
}
