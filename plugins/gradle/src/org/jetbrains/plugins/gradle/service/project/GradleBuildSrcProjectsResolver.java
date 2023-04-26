// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.Build;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.model.data.CompositeBuildData;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleDataKt;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getDefaultModuleTypeId;
import static org.jetbrains.plugins.gradle.util.GradleConstants.BUILD_SRC_NAME;

/**
 * @author Vladislav.Soroka
 */
public final class GradleBuildSrcProjectsResolver {
  public static final String BUILD_SRC_MODULE_PROPERTY = "buildSrcModule";
  @NotNull
  private final GradleProjectResolver myProjectResolver;
  @NotNull
  private final DefaultProjectResolverContext myResolverContext;
  @Nullable
  private final File myGradleUserHome;
  @Nullable
  private final GradleExecutionSettings myMainBuildExecutionSettings;
  @NotNull
  private final ExternalSystemTaskNotificationListener myListener;
  @NotNull
  private final ExternalSystemTaskId mySyncTaskId;
  @NotNull
  private final GradleProjectResolverExtension myResolverChain;

  public GradleBuildSrcProjectsResolver(@NotNull GradleProjectResolver projectResolver,
                                        @NotNull DefaultProjectResolverContext resolverContext,
                                        @Nullable File gradleUserHome,
                                        @Nullable GradleExecutionSettings mainBuildSettings,
                                        @NotNull ExternalSystemTaskNotificationListener listener,
                                        @NotNull ExternalSystemTaskId syncTaskId,
                                        @NotNull GradleProjectResolverExtension projectResolverChain) {
    myProjectResolver = projectResolver;
    myResolverContext = resolverContext;
    myGradleUserHome = gradleUserHome;
    myMainBuildExecutionSettings = mainBuildSettings;
    myListener = listener;
    mySyncTaskId = syncTaskId;
    myResolverChain = projectResolverChain;
  }

  public void discoverAndAppendTo(@NotNull DataNode<ProjectData> mainBuildProjectDataNode) {
    String gradleHome = myGradleUserHome == null ? null : myGradleUserHome.getPath();
    Index index = prepareIndexes(mainBuildProjectDataNode);

    List<String> jvmOptions = new SmartList<>();
    // the BuildEnvironment jvm arguments of the main build should be used for the 'buildSrc' import
    // to avoid spawning of the second gradle daemon
    BuildEnvironment mainBuildEnvironment = myResolverContext.getModels().getBuildEnvironment();
    if (mainBuildEnvironment != null) {
      jvmOptions.addAll(mainBuildEnvironment.getJava().getJvmArguments());
    }
    if (myMainBuildExecutionSettings != null) {
      jvmOptions.addAll(myMainBuildExecutionSettings.getJvmArguments());
    }

    Stream<Build> builds = new ToolingModelsProviderImpl(myResolverContext.getModels()).builds();
    builds.forEach(build -> {
      String buildPath = FileUtil.toSystemIndependentName(build.getBuildIdentifier().getRootDir().getPath());

      GradleExecutionSettings buildSrcProjectSettings;
      if (gradleHome != null) {
        if (myMainBuildExecutionSettings != null) {
          buildSrcProjectSettings = new GradleExecutionSettings(gradleHome,
                                                                myMainBuildExecutionSettings.getServiceDirectory(),
                                                                DistributionType.LOCAL,
                                                                myMainBuildExecutionSettings.isOfflineWork());
          buildSrcProjectSettings.setIdeProjectPath(myMainBuildExecutionSettings.getIdeProjectPath());
          buildSrcProjectSettings.setJavaHome(myMainBuildExecutionSettings.getJavaHome());
          buildSrcProjectSettings.setResolveModulePerSourceSet(myMainBuildExecutionSettings.isResolveModulePerSourceSet());
          buildSrcProjectSettings.setUseQualifiedModuleNames(myMainBuildExecutionSettings.isUseQualifiedModuleNames());
          buildSrcProjectSettings.setRemoteProcessIdleTtlInMs(myMainBuildExecutionSettings.getRemoteProcessIdleTtlInMs());
          buildSrcProjectSettings.setVerboseProcessing(myMainBuildExecutionSettings.isVerboseProcessing());
          buildSrcProjectSettings.setWrapperPropertyFile(myMainBuildExecutionSettings.getWrapperPropertyFile());
          buildSrcProjectSettings.setDelegatedBuild(myMainBuildExecutionSettings.isDelegatedBuild());
          buildSrcProjectSettings.withArguments(myMainBuildExecutionSettings.getArguments())
            .withEnvironmentVariables(myMainBuildExecutionSettings.getEnv())
            .passParentEnvs(myMainBuildExecutionSettings.isPassParentEnvs())
            .withVmOptions(jvmOptions);
          reuseTargetEnvironmentConfigurationProvider(buildSrcProjectSettings, myMainBuildExecutionSettings);
        }
        else {
          buildSrcProjectSettings = new GradleExecutionSettings(gradleHome, null, DistributionType.LOCAL, false);
        }
        includeRootBuildIncludedBuildsIfNeeded(buildSrcProjectSettings, index.compositeBuildData(), buildPath);
      }
      else {
        buildSrcProjectSettings = myMainBuildExecutionSettings;
      }

      final String buildSrcProjectPath = buildPath + "/buildSrc";
      DefaultProjectResolverContext buildSrcResolverCtx =
        new DefaultProjectResolverContext(mySyncTaskId, buildSrcProjectPath, buildSrcProjectSettings, myListener, myResolverContext.getPolicy(), false);
      myResolverContext.copyUserDataTo(buildSrcResolverCtx);
      String buildName = index.buildNames().get(buildPath);

      String buildSrcGroup = getBuildSrcGroup(buildPath, buildName);

      buildSrcResolverCtx.setBuildSrcGroup(buildSrcGroup);
      handleBuildSrcProject(mainBuildProjectDataNode,
                            buildName,
                            index.buildClasspathNodesMap().getModifiable(Paths.get(buildPath)),
                            index.includedModulesPaths(),
                            buildSrcResolverCtx,
                            myProjectResolver.getProjectDataFunction(buildSrcResolverCtx, myResolverChain, true));
    });
  }

  @NotNull
  public static Index prepareIndexes(@NotNull DataNode<ProjectData> mainBuildProjectDataNode) {
    ProjectData mainBuildProjectData = mainBuildProjectDataNode.getData();
    String projectPath = mainBuildProjectData.getLinkedExternalProjectPath();

    Map<String, String> includedBuildsPaths = new HashMap<>();
    Map<String, String> buildNames = new HashMap<>();
    buildNames.put(projectPath, mainBuildProjectData.getExternalName());
    CompositeBuildData compositeBuildData = getCompositeBuildData(mainBuildProjectDataNode);
    if (compositeBuildData != null) {
      for (BuildParticipant buildParticipant : compositeBuildData.getCompositeParticipants()) {
        String buildParticipantRootPath = buildParticipant.getRootPath();
        buildNames.put(buildParticipantRootPath, buildParticipant.getRootProjectName());
        for (String path : buildParticipant.getProjects()) {
          includedBuildsPaths.put(path, buildParticipantRootPath);
        }
      }
    }

    MultiMap<Path, DataNode<BuildScriptClasspathData>> buildClasspathNodesMap = new MultiMap<>();
    Map<String, DataNode<ModuleData>> includedModulesPaths = new HashMap<>();
    for (DataNode<ModuleData> moduleDataNode : findAll(mainBuildProjectDataNode, ProjectKeys.MODULE)) {
      String path = moduleDataNode.getData().getLinkedExternalProjectPath();
      includedModulesPaths.put(path, moduleDataNode);
      DataNode<BuildScriptClasspathData> scriptClasspathDataNode = find(moduleDataNode, BuildScriptClasspathData.KEY);
      if (scriptClasspathDataNode != null) {
        String rootPath = includedBuildsPaths.get(path);
        buildClasspathNodesMap.putValue(Paths.get(rootPath != null ? rootPath : projectPath), scriptClasspathDataNode);
      }
    }
    return new Index(buildNames, compositeBuildData, buildClasspathNodesMap, includedModulesPaths);
  }

  public record Index(Map<String, String> buildNames,
                       CompositeBuildData compositeBuildData,
                       MultiMap<Path, DataNode<BuildScriptClasspathData>> buildClasspathNodesMap,
                       Map<String, DataNode<ModuleData>> includedModulesPaths) {
  }

  private static void reuseTargetEnvironmentConfigurationProvider(@NotNull GradleExecutionSettings buildSrcProjectSettings,
                                                                  @NotNull GradleExecutionSettings mainBuildExecutionSettings) {
    TargetEnvironmentConfigurationProvider targetEnvironmentConfigurationProvider =
      ExternalSystemExecutionAware.Companion.getEnvironmentConfigurationProvider(mainBuildExecutionSettings);
    ExternalSystemExecutionAware.Companion
      .setEnvironmentConfigurationProvider(buildSrcProjectSettings, targetEnvironmentConfigurationProvider);
  }

  private void includeRootBuildIncludedBuildsIfNeeded(@NotNull GradleExecutionSettings buildSrcProjectSettings,
                                                      @Nullable CompositeBuildData compositeBuildData,
                                                      @NotNull String mainBuildPath) {
    if (compositeBuildData == null) return;
    String projectGradleVersion = myResolverContext.getProjectGradleVersion();
    GradleVersion gradleBaseVersion = projectGradleVersion == null ? null : GradleVersion.version(projectGradleVersion).getBaseVersion();
    if (gradleBaseVersion == null) return;

    // since 6.7 included builds become "visible" for `buildSrc` project https://docs.gradle.org/6.7-rc-1/release-notes.html#build-src
    // !!! Note, this is true only for builds included from the "root" build and it becomes visible also for "nested" `buildSrc` projects !!!
    // Transitive included builds are not visible even for related "transitive" `buildSrc` projects
    // due to limitation caused by specific ordering requirement:  "include order is important if an included build provides a plugin which should be discovered very very early".
    // It can be improved in the future Gradle releases.
    if (gradleBaseVersion.compareTo(GradleVersion.version("6.7")) < 0) return;
    // since 7.2 including builds that transitively include current buildSrc will produce errors: https://github.com/gradle/gradle/issues/20898
    for (BuildParticipant buildParticipant : excludeTransitiveParentsOf(mainBuildPath, compositeBuildData.getCompositeParticipants())) {
        buildSrcProjectSettings.withArguments(GradleConstants.INCLUDE_BUILD_CMD_OPTION, buildParticipant.getRootPath());
    }
  }

  @NotNull
  private static Collection<BuildParticipant> excludeTransitiveParentsOf(@NotNull String path, @NotNull List<BuildParticipant> participants) {
    Map<String, BuildParticipant> rootPathParticipantMap = new LinkedHashMap<>();

    for (BuildParticipant participant : participants) {
      rootPathParticipantMap.put(participant.getRootPath(), participant);
    }

    String currentPath = path;
    while (currentPath != null) {
      BuildParticipant removed = rootPathParticipantMap.remove(currentPath);
      currentPath = removed != null ? removed.getParentRootPath() : null;
    }

    return rootPathParticipantMap.values();
  }

  @Nullable
  private static CompositeBuildData getCompositeBuildData(@NotNull DataNode<ProjectData> mainBuildProjectDataNode) {
    DataNode<CompositeBuildData> compositeBuildDataNode = find(mainBuildProjectDataNode, CompositeBuildData.KEY);
    return compositeBuildDataNode != null ? compositeBuildDataNode.getData() : null;
  }

  private void handleBuildSrcProject(@NotNull DataNode<ProjectData> resultProjectDataNode,
                                     @Nullable String buildName,
                                     @NotNull Collection<DataNode<BuildScriptClasspathData>> buildClasspathNodes,
                                     @NotNull Map<String, DataNode<ModuleData>> includedModulesPaths,
                                     @NotNull DefaultProjectResolverContext buildSrcResolverCtx,
                                     @NotNull Function<ProjectConnection, DataNode<ProjectData>> projectConnectionDataNodeFunction) {
    final String projectPath = buildSrcResolverCtx.getProjectPath();
    File projectPathFile = new File(projectPath);
    if (!projectPathFile.isDirectory()) {
      return;
    }

    if (includedModulesPaths.containsKey(projectPath)) {
      // `buildSrc` has been already included into the main build (prohibited since 6.0, https://docs.gradle.org/current/userguide/upgrading_version_5.html#buildsrc_is_now_reserved_as_a_project_and_subproject_build_name)
      return;
    }

    if (ArrayUtil.isEmpty(projectPathFile.list((dir, name) -> !name.equals(".gradle") && !name.equals("build")))) {
      return;
    }

    if (buildSrcResolverCtx.isPreviewMode()) {
      ModuleData buildSrcModuleData =
        new ModuleData(":buildSrc", GradleConstants.SYSTEM_ID, getDefaultModuleTypeId(), BUILD_SRC_NAME, projectPath, projectPath);
      GradleModuleDataKt.setBuildSrcModule(buildSrcModuleData);
      resultProjectDataNode.createChild(ProjectKeys.MODULE, buildSrcModuleData);
      return;
    }

    final DataNode<ProjectData> buildSrcProjectDataNode = myProjectResolver.getHelper().execute(
      projectPath, buildSrcResolverCtx.getSettings(), mySyncTaskId, myListener, null, projectConnectionDataNodeFunction);

    if (buildSrcProjectDataNode == null) return;
    for (DataNode<LibraryData> libraryDataNode : getChildren(buildSrcProjectDataNode, ProjectKeys.LIBRARY)) {
      GradleProjectResolverUtil.linkProjectLibrary(resultProjectDataNode, libraryDataNode.getData());
    }

    Map<String, DataNode<? extends ModuleData>> buildSrcModules = new HashMap<>();

    boolean modulePerSourceSet = buildSrcResolverCtx.isResolveModulePerSourceSet();
    DataNode<? extends ModuleData> buildSrcModuleNode = null;
    for (DataNode<ModuleData> moduleNode : getChildren(buildSrcProjectDataNode, ProjectKeys.MODULE)) {
      final ModuleData moduleData = moduleNode.getData();
      buildSrcModules.put(moduleData.getId(), moduleNode);
      boolean isBuildSrcModule = BUILD_SRC_NAME.equals(moduleData.getExternalName());

      if (isBuildSrcModule && !modulePerSourceSet) {
        buildSrcModuleNode = moduleNode;
      }
      if (modulePerSourceSet) {
        for (DataNode<GradleSourceSetData> sourceSetNode : getChildren(moduleNode, GradleSourceSetData.KEY)) {
          buildSrcModules.put(sourceSetNode.getData().getId(), sourceSetNode);
          if (isBuildSrcModule && buildSrcModuleNode == null && sourceSetNode.getData().getExternalName().endsWith(":main")) {
            buildSrcModuleNode = sourceSetNode;
          }
        }
      }

      DataNode<ModuleData> includedModule = includedModulesPaths.get(moduleData.getLinkedExternalProjectPath());
      if (includedModule == null) {
        GradleModuleDataKt.setBuildSrcModule(moduleData);
        resultProjectDataNode.addChild(moduleNode);
        String[] moduleGroup = getModuleGroup(resultProjectDataNode, buildName, buildSrcResolverCtx, moduleData);
        if (moduleGroup != null) {
          moduleData.setIdeModuleGroup(moduleGroup);
        }
        for (DataNode<GradleSourceSetData> sourceSetNode : getChildren(moduleNode, GradleSourceSetData.KEY)) {
          if (moduleGroup != null) {
            sourceSetNode.getData().setIdeModuleGroup(moduleGroup);
          }
          getChildren(sourceSetNode, ProjectKeys.MODULE_DEPENDENCY).forEach(
            node -> maybeUpdateNonBuildSrcModuleDependencies(includedModulesPaths, node.getData()));
        }
      }
      else {
        GradleModuleDataKt.setBuildSrcModule(includedModule.getData());
      }
    }
    if (buildSrcModuleNode != null) {
      addBuildSrcToBuildScriptClasspathData(buildClasspathNodes, buildSrcModules, buildSrcModuleNode);
    }
  }

  public static void addBuildSrcToBuildScriptClasspathData(@NotNull Collection<DataNode<BuildScriptClasspathData>> buildClasspathNodes,
                                @NotNull Map<String, DataNode<? extends ModuleData>> buildSrcModules,
                                @NotNull DataNode<? extends ModuleData> buildSrcModuleNode) {
    Set<String> buildSrcRuntimeSourcesPaths = new HashSet<>();
    Set<String> buildSrcRuntimeClassesPaths = new HashSet<>();

    addSourcePaths(buildSrcRuntimeSourcesPaths, buildSrcModuleNode);

      for (DataNode<?> child : buildSrcModuleNode.getChildren()) {
        Object childData = child.getData();
        if (childData instanceof ModuleDependencyData moduleDependencyData && moduleDependencyData.getScope().isForProductionRuntime()) {
          DataNode<? extends ModuleData> depModuleNode = buildSrcModules.get(moduleDependencyData.getTarget().getId());
          if (depModuleNode != null) {
            addSourcePaths(buildSrcRuntimeSourcesPaths, depModuleNode);
          }
        }
        else if (childData instanceof LibraryDependencyData dependencyData) {
          // exclude generated gradle-api jar the gradle api classes/sources handled separately by BuildClasspathModuleGradleDataService
          if (dependencyData.getExternalName().startsWith("gradle-api-")) {
            continue;
          }
          LibraryData libraryData = dependencyData.getTarget();
          buildSrcRuntimeSourcesPaths.addAll(libraryData.getPaths(LibraryPathType.SOURCE));
          buildSrcRuntimeClassesPaths.addAll(libraryData.getPaths(LibraryPathType.BINARY));
        }
      }

    if (!buildSrcRuntimeSourcesPaths.isEmpty() || !buildSrcRuntimeClassesPaths.isEmpty()) {
      buildClasspathNodes.forEach(classpathNode -> {
        BuildScriptClasspathData copyFrom = classpathNode.getData();

        List<BuildScriptClasspathData.ClasspathEntry> classpathEntries = new ArrayList<>(copyFrom.getClasspathEntries().size() + 1);
        classpathEntries.addAll(copyFrom.getClasspathEntries());
        classpathEntries.add(BuildScriptClasspathData.ClasspathEntry.create(
          new HashSet<>(buildSrcRuntimeClassesPaths),
          new HashSet<>(buildSrcRuntimeSourcesPaths),
          Collections.emptySet()
        ));

        BuildScriptClasspathData buildScriptClasspathData = new BuildScriptClasspathData(GradleConstants.SYSTEM_ID, classpathEntries);
        buildScriptClasspathData.setGradleHomeDir(copyFrom.getGradleHomeDir());

        DataNode<?> parent = classpathNode.getParent();
        assert parent != null;
        parent.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData);
        classpathNode.clear(true);
      });
    }
  }

  private static void maybeUpdateNonBuildSrcModuleDependencies(@NotNull Map<String, DataNode<ModuleData>> includedModulesPaths,
                                                               @NotNull ModuleDependencyData moduleDependencyData) {
    ModuleData target = moduleDependencyData.getTarget();
    DataNode<ModuleData> moduleDataResolvedForMainBuild = includedModulesPaths.get(target.getLinkedExternalProjectPath());
    if (moduleDataResolvedForMainBuild != null && target instanceof GradleSourceSetData) {
      String targetModuleName = target.getModuleName();
      DataNode<GradleSourceSetData> nonBuildSrcModule = find(moduleDataResolvedForMainBuild, GradleSourceSetData.KEY,
                                                 node -> targetModuleName.equals(node.getData().getModuleName()));
      if (nonBuildSrcModule != null) {
        moduleDependencyData.setTarget(nonBuildSrcModule.getData());
      }
    }
  }

  private static String @Nullable [] getModuleGroup(@NotNull DataNode<ProjectData> resultProjectDataNode,
                                                    @Nullable String buildName,
                                                    @NotNull DefaultProjectResolverContext buildSrcResolverCtx,
                                                    ModuleData moduleData) {
    if (!buildSrcResolverCtx.isUseQualifiedModuleNames() && moduleData.getIdeModuleGroup() != null) {
      String buildNamePrefix = isNotEmpty(buildName) ? buildName : resultProjectDataNode.getData().getInternalName();
      return ArrayUtil.prepend(buildNamePrefix, moduleData.getIdeModuleGroup());
    }
    return null;
  }

  private static void addSourcePaths(Set<String> paths, DataNode<? extends ModuleData> moduleNode) {
    getChildren(moduleNode, ProjectKeys.CONTENT_ROOT)
      .stream()
      .flatMap(contentNode -> contentNode.getData().getPaths(ExternalSystemSourceType.SOURCE).stream())
      .map(ContentRootData.SourceRoot::getPath)
      .forEach(paths::add);
  }

  @NotNull
  private static String getBuildSrcGroup(String buildPath, String buildName) {
    if (isEmpty(buildName)) {
      return new File(buildPath).getName();
    } else {
      return buildName;
    }
  }
}
