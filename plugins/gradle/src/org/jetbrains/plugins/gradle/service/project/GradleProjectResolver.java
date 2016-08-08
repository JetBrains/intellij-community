/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.gradle.tooling.*;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.remote.impl.GradleLibraryNamesMixer;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.execution.UnsupportedCancellationToken;
import org.jetbrains.plugins.gradle.settings.ClassHolder;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.*;

import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.attachGradleSdkSources;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleId;

/**
 * @author Denis Zhdanov, Vladislav Soroka
 * @since 8/8/11 11:09 AM
 */
public class GradleProjectResolver implements ExternalSystemProjectResolver<GradleExecutionSettings> {

  private static final Logger LOG = Logger.getInstance("#" + GradleProjectResolver.class.getName());

  @NotNull private final GradleExecutionHelper myHelper;
  private final GradleLibraryNamesMixer myLibraryNamesMixer = new GradleLibraryNamesMixer();

  private final MultiMap<ExternalSystemTaskId, CancellationTokenSource> myCancellationMap = MultiMap.create();
  public static final Key<Map<String/* module id */, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>>> RESOLVED_SOURCE_SETS =
    Key.create("resolvedSourceSets");
  public static final Key<Map<String/* output path */, Pair<String /* module id*/, ExternalSystemSourceType>>> MODULES_OUTPUTS =
    Key.create("moduleOutputsMap");
  public static final Key<Map<ExternalSystemSourceType, String /* output path*/>> GRADLE_OUTPUTS = Key.create("gradleOutputs");
  public static final Key<Map<String/* artifact path */, String /* module id*/>> CONFIGURATION_ARTIFACTS =
    Key.create("gradleArtifactsMap");

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
    if (settings != null) {
      myHelper.ensureInstalledWrapper(id, projectPath, settings, listener);
    }

    final GradleProjectResolverExtension projectResolverChain = createProjectResolverChain(settings);
    DefaultProjectResolverContext resolverContext = new DefaultProjectResolverContext(id, projectPath, settings, listener, isPreviewMode);
    final DataNode<ProjectData> resultProjectDataNode = myHelper.execute(
      projectPath, settings, new ProjectConnectionDataNodeFunction(resolverContext, projectResolverChain, false)
    );

    // auto-discover buildSrc project if needed
    final String buildSrcProjectPath = projectPath + "/buildSrc";
    DefaultProjectResolverContext buildSrcResolverCtx =
      new DefaultProjectResolverContext(id, buildSrcProjectPath, settings, listener, isPreviewMode);
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

    final ProjectImportAction projectImportAction = new ProjectImportAction(resolverCtx.isPreviewMode());

    final List<KeyValue<String, String>> extraJvmArgs = new ArrayList<>();
    final List<String> commandLineArgs = ContainerUtil.newArrayList();
    final Set<Class> toolingExtensionClasses = ContainerUtil.newHashSet();

    if(resolverCtx.isPreviewMode()){
      commandLineArgs.add("-Didea.isPreviewMode=true");
      final Set<Class> previewLightWeightToolingModels = ContainerUtil.set(ExternalProjectPreview.class, GradleBuild.class);
      projectImportAction.addExtraProjectModelClasses(previewLightWeightToolingModels);
    }
    if(resolverCtx.isResolveModulePerSourceSet()) {
      commandLineArgs.add("-Didea.resolveSourceSetDependencies=true");
    }

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
        extraJvmArgs.addAll(resolverExtension.getExtraJvmArgs());
      }
      // collect extra command-line arguments
      commandLineArgs.addAll(resolverExtension.getExtraCommandLineArgs());
      // collect tooling extensions classes
      toolingExtensionClasses.addAll(resolverExtension.getToolingExtensionsClasses());
    }

    final ParametersList parametersList = new ParametersList();
    for (KeyValue<String, String> jvmArg : extraJvmArgs) {
      parametersList.addProperty(jvmArg.getKey(), jvmArg.getValue());
    }

    final BuildEnvironment buildEnvironment = GradleExecutionHelper.getBuildEnvironment(resolverCtx.getConnection());
    GradleVersion gradleVersion = null;
    if (buildEnvironment != null) {
      gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
    }

    BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor = resolverCtx.getConnection().action(projectImportAction);

    File initScript = GradleExecutionHelper.generateInitScript(isBuildSrcProject, toolingExtensionClasses);
    if (initScript != null) {
      ContainerUtil.addAll(commandLineArgs, GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.getAbsolutePath());
    }

    GradleExecutionHelper.prepare(
      buildActionExecutor, resolverCtx.getExternalSystemTaskId(),
      resolverCtx.getSettings(), resolverCtx.getListener(),
      parametersList.getParameters(), commandLineArgs, resolverCtx.getConnection());

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
        resolverCtx.getSettings(),
        resolverCtx.getConnection(),
        resolverCtx.getListener(),
        parametersList.getParameters());

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
    extractExternalProjectModels(allModels, resolverCtx.isPreviewMode());
    resolverCtx.setModels(allModels);

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
    for (IdeaModule gradleModule : gradleModules) {
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
      String mainModuleId = getModuleId(gradleModule);
      moduleMap.put(mainModuleId, Pair.create(moduleDataNode, gradleModule));
    }

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

    for (final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values()) {
      final DataNode<ModuleData> moduleDataNode = pair.first;
      final IdeaModule ideaModule = pair.second;
      projectResolverChain.populateModuleDependencies(ideaModule, moduleDataNode, projectDataNode);
      projectResolverChain.populateModuleExtraModels(ideaModule, moduleDataNode);
    }
    mergeSourceSetContentRoots(moduleMap, resolverCtx);
    if(resolverCtx.isResolveModulePerSourceSet()) {
      mergeLibraryAndModuleDependencyData(projectDataNode, gradleHomeDir, gradleVersion);
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

  private static void mergeLibraryAndModuleDependencyData(DataNode<ProjectData> projectDataNode,
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
      if (!libraryDependencyDataNode.getChildren().isEmpty()) continue;

      final DataNode<?> libraryNodeParent = libraryDependencyDataNode.getParent();
      if (libraryNodeParent == null) continue;

      final LibraryDependencyData libraryDependencyData = libraryDependencyDataNode.getData();
      final LibraryData libraryData = libraryDependencyData.getTarget();
      final Set<String> libraryPaths = libraryData.getPaths(LibraryPathType.BINARY);
      if (libraryPaths.isEmpty()) continue;
      if(StringUtil.isNotEmpty(libraryData.getExternalName())) continue;

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
          Map<ExternalSystemSourceType, String> gradleOutputs = pair.first.getUserData(GRADLE_OUTPUTS);
          if(gradleOutputs != null) {
            ContainerUtil.addAllNotNull(compileSet,
                                        gradleOutputs.get(ExternalSystemSourceType.SOURCE),
                                        gradleOutputs.get(ExternalSystemSourceType.RESOURCE));
          }
          if (!compileSet.isEmpty() && ContainerUtil.intersects(libraryPaths, compileSet)) {
            targetModuleOutputPaths = compileSet;
          }
          else {
            final Set<String> testSet = ContainerUtil.newHashSet();
            if(gradleOutputs != null) {
              ContainerUtil.addAllNotNull(testSet,
                                          gradleOutputs.get(ExternalSystemSourceType.TEST),
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
            libraryNodeParent.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData);
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

  private static Map<String, ExternalProject> extractExternalProjectModels(ProjectImportAction.AllModels models, boolean isPreview) {
    final Class<? extends ExternalProject> modelClazz = isPreview ? ExternalProjectPreview.class : ExternalProject.class;
    final ExternalProject externalRootProject =   models.getExtraProject(null, modelClazz);
    if (externalRootProject == null) return Collections.emptyMap();

    final DefaultExternalProject wrappedExternalRootProject = new DefaultExternalProject(externalRootProject);
    models.addExtraProject(wrappedExternalRootProject, ExternalProject.class);
    final Map<String, ExternalProject> externalProjectsMap = createExternalProjectsMap(wrappedExternalRootProject);

    DomainObjectSet<? extends IdeaModule> gradleModules = models.getIdeaProject().getModules();
    if (gradleModules != null && !gradleModules.isEmpty()) {
      for (IdeaModule ideaModule : gradleModules) {
        final ExternalProject externalProject = externalProjectsMap.get(getModuleId(ideaModule));
        if (externalProject != null) {
          models.addExtraProject(externalProject, ExternalProject.class, ideaModule);
        }
      }
    }

    return externalProjectsMap;
  }

  private static Map<String, ExternalProject> createExternalProjectsMap(@Nullable final ExternalProject rootExternalProject) {
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
    if (!new File(projectPath).isDirectory()) {
      return;
    }

    if (projectConnectionDataNodeFunction.myResolverContext.isPreviewMode()) {
      ModuleData buildSrcModuleData =
        new ModuleData(":buildSrc", GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), "buildSrc", projectPath, projectPath);
      resultProjectDataNode.createChild(ProjectKeys.MODULE, buildSrcModuleData);
      return;
    }

    final DataNode<ModuleData> buildSrcModuleDataNode =
      GradleProjectResolverUtil.findModule(resultProjectDataNode, projectPath);

    // check if buildSrc project was already exposed in settings.gradle file
    if (buildSrcModuleDataNode != null) return;

    final DataNode<ProjectData> buildSrcProjectDataDataNode = myHelper.execute(
      projectPath, projectConnectionDataNodeFunction.myResolverContext.getSettings(), projectConnectionDataNodeFunction);

    if (buildSrcProjectDataDataNode != null) {
      for (DataNode<ModuleData> moduleNode : ExternalSystemApiUtil.getChildren(buildSrcProjectDataDataNode, ProjectKeys.MODULE)) {
        resultProjectDataNode.addChild(moduleNode);

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
}
