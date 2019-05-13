/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemDebugEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.LinkedMultiMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.EqualityPolicy;
import org.gradle.api.artifacts.Dependency;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId;
import org.jetbrains.plugins.gradle.ExternalDependencyId;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.CONFIGURATION_ARTIFACTS;

/**
 * @author Vladislav.Soroka
 */
public class GradleProjectResolverUtil {
  private static final Logger LOG = Logger.getInstance(GradleProjectResolverUtil.class);
  @NotNull
  private static final Key<Object> CONTAINER_KEY = Key.create(Object.class, ExternalSystemConstants.UNORDERED);
  public static final String BUILD_SRC_NAME = "buildSrc";

  @NotNull
  public static DataNode<ModuleData> createMainModule(@NotNull ProjectResolverContext resolverCtx,
                                                      @NotNull IdeaModule gradleModule,
                                                      @NotNull DataNode<ProjectData> projectDataNode) {
    final String moduleName = gradleModule.getName();
    if (moduleName == null) {
      throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
    }

    final ProjectData projectData = projectDataNode.getData();
    final String mainModuleConfigPath = getModuleConfigPath(resolverCtx, gradleModule, projectData.getLinkedExternalProjectPath());
    final String ideProjectPath = resolverCtx.getIdeProjectPath();
    final String relativePath;
    if (FileUtil.isAncestor(projectData.getLinkedExternalProjectPath(), mainModuleConfigPath, false)) {
      relativePath = FileUtil.getRelativePath(projectData.getLinkedExternalProjectPath(), mainModuleConfigPath, '/');
    }
    else {
      relativePath = String.valueOf(FileUtil.pathHashCode(mainModuleConfigPath));
    }
    final String mainModuleFileDirectoryPath =
      ideProjectPath == null
      ? mainModuleConfigPath
      : ideProjectPath + '/' + (relativePath == null || relativePath.equals(".") ? "" : relativePath);
    if (ExternalSystemDebugEnvironment.DEBUG_ORPHAN_MODULES_PROCESSING) {
      LOG.info(String.format(
        "Creating module data ('%s') with the external config path: '%s'", gradleModule.getGradleProject().getPath(), mainModuleConfigPath
      ));
    }

    String mainModuleId = getModuleId(resolverCtx, gradleModule);
    final ModuleData moduleData =
      new ModuleData(mainModuleId, GradleConstants.SYSTEM_ID, getDefaultModuleTypeId(), moduleName,
                     mainModuleFileDirectoryPath, mainModuleConfigPath);

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (externalProject != null) {
      moduleData.setInternalName(getInternalModuleName(gradleModule, externalProject, resolverCtx));
      moduleData.setGroup(externalProject.getGroup());
      moduleData.setVersion(externalProject.getVersion());
      moduleData.setDescription(externalProject.getDescription());
      if (!resolverCtx.isResolveModulePerSourceSet()) {
        moduleData.setArtifacts(externalProject.getArtifacts());
        moduleData.setPublication(new ProjectId(externalProject.getGroup(),
                                                externalProject.getName(),
                                                externalProject.getVersion()));
      }
    }

    return projectDataNode.createChild(ProjectKeys.MODULE, moduleData);
  }

  public static String getDefaultModuleTypeId() {
    ModuleType moduleType = ModuleTypeManager.getInstance().getDefaultModuleType();
    return moduleType.getId();
  }

  @NotNull
  static String getInternalModuleName(@NotNull IdeaModule gradleModule, @NotNull ExternalProject externalProject,
                                      @NotNull ProjectResolverContext resolverCtx) {
    return getInternalModuleName(gradleModule, externalProject, null, resolverCtx);
  }

  @NotNull
  static String getInternalModuleName(@NotNull IdeaModule gradleModule,
                                      @NotNull ExternalProject externalProject,
                                      @Nullable String sourceSetName,
                                      @NotNull ProjectResolverContext resolverCtx) {
    String delimiter;
    StringBuilder moduleName = new StringBuilder();
    String buildSrcGroup = resolverCtx.getBuildSrcGroup();
    if (resolverCtx.isUseQualifiedModuleNames()) {
      delimiter = ".";
      if (StringUtil.isNotEmpty(buildSrcGroup)) {
        moduleName.append(buildSrcGroup).append(delimiter);
      }
      moduleName.append(gradlePathToQualifiedName(gradleModule.getProject().getName(), externalProject.getQName()));
    }
    else {
      delimiter = "_";
      if (StringUtil.isNotEmpty(buildSrcGroup)) {
        moduleName.append(buildSrcGroup).append(delimiter);
      }
      moduleName.append(gradleModule.getName());
    }
    if (sourceSetName != null) {
      assert !sourceSetName.isEmpty();
      moduleName.append(delimiter);
      moduleName.append(sourceSetName);
    }
    return PathUtilRt.suggestFileName(moduleName.toString(), true, false);
  }

  @NotNull
  private static String gradlePathToQualifiedName(@NotNull String rootName,
                                                  @NotNull String gradlePath) {
    return
      (gradlePath.startsWith(":") ? rootName + "." : "")
      + Arrays.stream(gradlePath.split(":"))
        .filter(s -> !s.isEmpty())
        .collect(Collectors.joining("."));
  }

  @NotNull
  public static String getModuleConfigPath(@NotNull ProjectResolverContext resolverCtx,
                                           @NotNull IdeaModule gradleModule,
                                           @NotNull String rootProjectPath) {
    GradleBuild build = resolverCtx.getExtraProject(gradleModule, GradleBuild.class);
    if (build != null) {
      String gradlePath = gradleModule.getGradleProject().getPath();
      File moduleDirPath = getModuleDirPath(build, gradlePath);
      if (moduleDirPath == null) {
        throw new IllegalStateException(String.format("Unable to find root directory for module '%s'", gradleModule.getName()));
      }
      try {
        return ExternalSystemApiUtil.toCanonicalPath(moduleDirPath.getCanonicalPath());
      }
      catch (IOException e) {
        LOG.warn("construction of the canonical path for the module fails", e);
      }
    }

    return GradleUtil.getConfigPath(gradleModule.getGradleProject(), rootProjectPath);
  }

  /**
   * Returns the physical path of the module's root directory (the path in the file system.)
   * <p>
   * It is important to note that Gradle has its own "logical" path that may or may not be equal to the physical path of a Gradle project.
   * For example, the sub-project at ${projectRootDir}/apps/app will have the Gradle path :apps:app. Gradle also allows mapping physical
   * paths to a different logical path. For example, in settings.gradle:
   * <pre>
   *   include ':app'
   *   project(':app').projectDir = new File(rootDir, 'apps/app')
   * </pre>
   * In this example, sub-project at ${projectRootDir}/apps/app will have the Gradle path :app.
   * </p>
   *
   * @param build contains information about the root Gradle project and its sub-projects. Such information includes the physical path of
   *              the root Gradle project and its sub-projects.
   * @param path  the Gradle "logical" path. This path uses colon as separator, and may or may not be equal to the physical path of a
   *              Gradle project.
   * @return the physical path of the module's root directory.
   */
  @Nullable
  public static File getModuleDirPath(@NotNull GradleBuild build, @NotNull String path) {
    for (BasicGradleProject project: build.getProjects()) {
      if (project.getPath().equals(path)) {
        return project.getProjectDirectory();
      }
    }
    return null;
  }

  @NotNull
  public static String getModuleId(@NotNull ProjectResolverContext resolverCtx, @NotNull IdeaModule gradleModule) {
    GradleProject gradleProject = gradleModule.getGradleProject();
    String gradlePath = gradleProject.getPath();
    String compositePrefix = "";
    if (gradleModule.getProject() != resolverCtx.getModels().getIdeaProject()) {
      if (!StringUtil.isEmpty(gradlePath) && !":".equals(gradlePath)) {
        compositePrefix = gradleModule.getProject().getName();
      }
    }
    return compositePrefix + getModuleId(gradlePath, gradleModule.getName());
  }

  @NotNull
  public static String getModuleId(String gradlePath, String moduleName) {
    return StringUtil.isEmpty(gradlePath) || ":".equals(gradlePath) ? moduleName : gradlePath;
  }

  @NotNull
  public static String getModuleId(@NotNull ExternalProject externalProject) {
    return externalProject.getId();
  }

  @NotNull
  public static String getModuleId(@NotNull ExternalProject externalProject, @NotNull ExternalSourceSet sourceSet) {
    String mainModuleId = getModuleId(externalProject);
    return mainModuleId + ":" + sourceSet.getName();
  }

  @NotNull
  public static String getModuleId(@NotNull ProjectResolverContext resolverCtx,
                                   @NotNull IdeaModule gradleModule,
                                   @NotNull ExternalSourceSet sourceSet) {
    String mainModuleId = getModuleId(resolverCtx, gradleModule);
    return mainModuleId + ":" + sourceSet.getName();
  }

  @NotNull
  public static String getModuleId(@NotNull ExternalProjectDependency projectDependency) {
    DependencyScope dependencyScope = getDependencyScope(projectDependency.getScope());
    String projectPath = projectDependency.getProjectPath();
    String moduleId = StringUtil.isEmpty(projectPath) || ":".equals(projectPath) ? projectDependency.getName() : projectPath;
    if (Dependency.DEFAULT_CONFIGURATION.equals(projectDependency.getConfigurationName())) {
      moduleId += dependencyScope == DependencyScope.TEST ? ":test" : ":main";
    }
    else {
      moduleId += (':' + projectDependency.getConfigurationName());
    }
    return moduleId;
  }

  @Nullable
  public static String getSourceSetName(final Module module) {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return null;
    if (!GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(ExternalSystemApiUtil.getExternalModuleType(module))) return null;

    String externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module);
    if (externalProjectId == null) return null;
    int i = externalProjectId.lastIndexOf(':');
    if (i == -1 || externalProjectId.length() < i + 1) return null;

    return externalProjectId.substring(i + 1);
  }

  @Nullable
  public static String getGradlePath(final Module module) {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return null;
    final String externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module);
    if (externalProjectId == null) return null;

    final String moduleType = ExternalSystemApiUtil.getExternalModuleType(module);
    boolean trimSourceSet = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(moduleType);
    final List<String> pathParts = StringUtil.split(externalProjectId, ":");
    if (!externalProjectId.startsWith(":") && !pathParts.isEmpty()) pathParts.remove(0);
    if (trimSourceSet && !pathParts.isEmpty()) pathParts.remove(pathParts.size() - 1);
    String join = StringUtil.join(pathParts, ":");
    return join.isEmpty() ? ":" : ":" + join;
  }

  @NotNull
  public static DependencyScope getDependencyScope(@Nullable String scope) {
    return scope != null ? DependencyScope.valueOf(scope) : DependencyScope.COMPILE;
  }

  public static void attachGradleSdkSources(@NotNull final IdeaModule gradleModule,
                                            @Nullable final File libFile,
                                            @NotNull final LibraryData library,
                                            @NotNull final ProjectResolverContext resolverCtx) {
    if (libFile == null || !libFile.getName().startsWith("gradle-")) return;
    final BuildScriptClasspathModel buildScriptClasspathModel =
      resolverCtx.getExtraProject(gradleModule, BuildScriptClasspathModel.class);
    if (buildScriptClasspathModel == null) return;
    final File gradleHomeDir = buildScriptClasspathModel.getGradleHomeDir();
    if (gradleHomeDir == null) return;
    final GradleVersion gradleVersion = GradleVersion.version(buildScriptClasspathModel.getGradleVersion());
    attachGradleSdkSources(libFile, library, gradleHomeDir, gradleVersion);
  }

  public static void attachGradleSdkSources(@Nullable final File libFile,
                                            @NotNull final LibraryData library,
                                            @NotNull final File gradleHomeDir,
                                            @NotNull final GradleVersion gradleVersion) {
    if (libFile == null || !libFile.getName().startsWith("gradle-")) return;
    if (!FileUtil.isAncestor(gradleHomeDir, libFile, true)) {
      File libFileParent = libFile.getParentFile();
      if (libFileParent == null || !StringUtil.equals("generated-gradle-jars", libFileParent.getName())) return;
      if (("gradle-api-" + gradleVersion.getVersion() + ".jar").equals(libFile.getName())) {
        File gradleSrc = new File(gradleHomeDir, "src");
        File[] gradleSrcRoots = gradleSrc.listFiles();
        if (gradleSrcRoots == null) return;
        for (File srcRoot: gradleSrcRoots) {
          library.addPath(LibraryPathType.SOURCE, srcRoot.getAbsolutePath());
        }
      }
      return;
    }

    File libOrPluginsFile = libFile.getParentFile();
    if (libOrPluginsFile != null && ("plugins".equals(libOrPluginsFile.getName()))) {
      libOrPluginsFile = libOrPluginsFile.getParentFile();
    }

    if (libOrPluginsFile != null && "lib".equals(libOrPluginsFile.getName()) && libOrPluginsFile.getParentFile() != null) {
      File srcDir = new File(libOrPluginsFile.getParentFile(), "src");

      if (gradleVersion.compareTo(GradleVersion.version("1.9")) >= 0) {
        int endIndex = libFile.getName().indexOf(gradleVersion.getVersion());
        if (endIndex != -1) {
          String srcDirChild = libFile.getName().substring("gradle-".length(), endIndex - 1);
          srcDir = new File(srcDir, srcDirChild);
        }
      }

      if (srcDir.isDirectory()) {
        library.addPath(LibraryPathType.SOURCE, srcDir.getAbsolutePath());
      }
    }
  }

  public static void attachSourcesAndJavadocFromGradleCacheIfNeeded(File gradleUserHomeDir, LibraryData libraryData) {
    if (!libraryData.getPaths(LibraryPathType.SOURCE).isEmpty() && !libraryData.getPaths(LibraryPathType.DOC).isEmpty()) {
      return;
    }

    for (String path: libraryData.getPaths(LibraryPathType.BINARY)) {
      try {
        final Path file = Paths.get(path);
        if (!FileUtil.isAncestor(gradleUserHomeDir.getPath(), path, true)) continue;
        Path binaryFileParent = file.getParent();
        Path grandParentFile = binaryFileParent.getParent();

        final boolean[] sourceFound = {false};
        final boolean[] docFound = {false};
        Files.walkFileTree(grandParentFile, EnumSet.noneOf(FileVisitOption.class), 2, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (binaryFileParent.equals(dir)) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            return super.preVisitDirectory(dir, attrs);
          }

          @Override
          public FileVisitResult visitFile(Path sourceCandidate, BasicFileAttributes attrs) throws IOException {
            if (!sourceCandidate.getParent().getParent().equals(grandParentFile)) {
              return FileVisitResult.SKIP_SIBLINGS;
            }
            if (attrs.isRegularFile()) {
              if (StringUtil.endsWith(sourceCandidate.getFileName().toString(), "-sources.jar")) {
                libraryData.addPath(LibraryPathType.SOURCE, sourceCandidate.toFile().getAbsolutePath());
                sourceFound[0] = true;
              }
              else if (StringUtil.endsWith(sourceCandidate.getFileName().toString(), "-javadoc.jar")) {
                libraryData.addPath(LibraryPathType.DOC, sourceCandidate.toFile().getAbsolutePath());
                docFound[0] = true;
              }
            }
            if (sourceFound[0] && docFound[0]) {
              return FileVisitResult.TERMINATE;
            }
            return super.visitFile(file, attrs);
          }
        });
      }
      catch (IOException | InvalidPathException e) {
        LOG.debug(e);
      }
    }
  }

  @Deprecated
  @SuppressWarnings("unchecked")
  public static Collection<DependencyData> getIdeDependencies(@NotNull ProjectResolverContext resolverCtx,
                                                              @NotNull DataNode<? extends ModuleData> moduleDataNode,
                                                              @NotNull Collection<ExternalDependency> dependencies)
    throws IllegalStateException {

    final DataNode<ProjectData> ideProject = ExternalSystemApiUtil.findParent(moduleDataNode, ProjectKeys.PROJECT);
    assert ideProject != null;

    final Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap =
      ideProject.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS);
    assert sourceSetMap != null;

    final Map<String, String> artifactsMap = ideProject.getUserData(CONFIGURATION_ARTIFACTS);
    assert artifactsMap != null;

    DataNode fakeNode = new DataNode(CONTAINER_KEY, moduleDataNode.getData(), null);
    buildDependencies(resolverCtx, sourceSetMap, artifactsMap, fakeNode, dependencies, null);
    final Collection<DataNode<?>> dataNodes =
      ExternalSystemApiUtil.findAllRecursively(fakeNode, node -> node.getData() instanceof DependencyData);
    return ContainerUtil.map(dataNodes, node -> (DependencyData)node.getData());
  }

  public static void buildDependencies(@NotNull ProjectResolverContext resolverCtx,
                                       @NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
                                       @NotNull final Map<String, String> artifactsMap,
                                       @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                       @NotNull Collection<ExternalDependency> dependencies,
                                       @Nullable DataNode<ProjectData> ideProject) throws IllegalStateException {
    Map<ExternalDependencyId, ExternalDependency> dependencyMap = ContainerUtil.newHashMap();

    Queue<ExternalDependency> queue = ContainerUtil.newLinkedList(dependencies);
    while (!queue.isEmpty()) {
      final ExternalDependency dependency = queue.remove();
      ExternalDependency seenDependency = dependencyMap.get(dependency.getId());
      if (seenDependency != null) {
        if (dependency instanceof ExternalLibraryDependency) {
          if (seenDependency instanceof ExternalLibraryDependency &&
              !FileUtil.filesEqual(((ExternalLibraryDependency)seenDependency).getFile(),
                                   ((ExternalLibraryDependency)dependency).getFile())) {
            DefaultExternalMultiLibraryDependency mergedDependency = new DefaultExternalMultiLibraryDependency();
            mergedDependency.setName(dependency.getId().getName());
            mergedDependency.setGroup(dependency.getId().getGroup());
            mergedDependency.setVersion(dependency.getId().getVersion());
            mergedDependency.setPackaging(dependency.getId().getPackaging());
            mergedDependency.setClassifier(dependency.getId().getClassifier());
            mergedDependency.setScope(dependency.getScope());
            mergedDependency.setClasspathOrder(dependency.getClasspathOrder());
            mergedDependency.getDependencies().addAll(dependency.getDependencies());

            mergedDependency.getFiles().addAll(ContainerUtil.packNullables(
              ((ExternalLibraryDependency)seenDependency).getFile(), ((ExternalLibraryDependency)dependency).getFile()));
            mergedDependency.getSources().addAll((ContainerUtil.packNullables(
              ((ExternalLibraryDependency)seenDependency).getSource(), ((ExternalLibraryDependency)dependency).getSource())));
            mergedDependency.getJavadoc().addAll((ContainerUtil.packNullables(
              ((ExternalLibraryDependency)seenDependency).getJavadoc(), ((ExternalLibraryDependency)dependency).getJavadoc())));

            dependencyMap.put(dependency.getId(), mergedDependency);
            continue;
          }
          else if (seenDependency instanceof DefaultExternalMultiLibraryDependency) {
            DefaultExternalMultiLibraryDependency mergedDependency = (DefaultExternalMultiLibraryDependency)seenDependency;
            mergedDependency.getFiles().addAll(ContainerUtil.packNullables(((ExternalLibraryDependency)dependency).getFile()));
            mergedDependency.getSources().addAll(ContainerUtil.packNullables(((ExternalLibraryDependency)dependency).getSource()));
            mergedDependency.getJavadoc().addAll(ContainerUtil.packNullables(((ExternalLibraryDependency)dependency).getJavadoc()));
            continue;
          }
        }

        DependencyScope prevScope =
          seenDependency.getScope() == null ? DependencyScope.COMPILE : DependencyScope.valueOf(seenDependency.getScope());
        DependencyScope currentScope =
          dependency.getScope() == null ? DependencyScope.COMPILE : DependencyScope.valueOf(dependency.getScope());

        if (prevScope.isForProductionCompile()) continue;
        if (prevScope.isForProductionRuntime() && currentScope.isForProductionRuntime()) continue;
      }

      dependencyMap.put(new DefaultExternalDependencyId(dependency.getId()), dependency);
      queue.addAll(dependency.getDependencies());
    }

    doBuildDependencies(resolverCtx, sourceSetMap, artifactsMap, dependencyMap, ownerDataNode, dependencies, ideProject);
  }

  private static void doBuildDependencies(@NotNull ProjectResolverContext resolverCtx,
                                          @NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
                                          @NotNull final Map<String, String> artifactsMap,
                                          @NotNull Map<ExternalDependencyId, ExternalDependency> mergedDependencyMap,
                                          @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                          @NotNull Collection<ExternalDependency> dependencies,
                                          @Nullable DataNode<ProjectData> ideProject) throws IllegalStateException {

    Map<ExternalDependencyId, ExternalDependency> dependencyMap = ContainerUtil.newLinkedHashMap();
    for (ExternalDependency dependency: dependencies) {
      final ExternalDependency dep = dependencyMap.get(dependency.getId());
      if (dep instanceof AbstractExternalDependency) {
        dep.getDependencies().addAll(ContainerUtil.subtract(dependency.getDependencies(), dep.getDependencies()));
      }
      else {
        dependencyMap.put(dependency.getId(), dependency);
      }
    }

    for (ExternalDependency dependency: dependencyMap.values()) {
      final ExternalDependency mergedDependency = ContainerUtil.getOrElse(mergedDependencyMap, dependency.getId(), dependency);
      DependencyScope dependencyScope = getDependencyScope(mergedDependency.getScope());

      ModuleData ownerModule = null;
      if (ownerDataNode.getData() instanceof ModuleData) {
        ownerModule = (ModuleData)ownerDataNode.getData();
      }
      else if (ownerDataNode.getData() instanceof DependencyData) {
        ownerModule = ((DependencyData)ownerDataNode.getData()).getOwnerModule();
      }

      assert ownerModule != null;

      DataNode<? extends ExternalEntityData> depOwnerDataNode = null;
      if (mergedDependency instanceof ExternalProjectDependency) {
        class ProjectDependencyInfo {
          @NotNull ModuleData myModuleData;
          @Nullable ExternalSourceSet mySourceSet;
          Collection<File> dependencyArtifacts;

          ProjectDependencyInfo(@NotNull ModuleData moduleData,
                                       @Nullable ExternalSourceSet sourceSet,
                                       Collection<File> dependencyArtifacts) {
            this.myModuleData = moduleData;
            this.mySourceSet = sourceSet;
            this.dependencyArtifacts = dependencyArtifacts;
          }
        }

        final ExternalProjectDependency projectDependency = (ExternalProjectDependency)mergedDependency;

        Collection<ProjectDependencyInfo> projectDependencyInfos = ContainerUtil.newArrayList();
        String selectionReason = projectDependency.getSelectionReason();
        if ("composite build substitution".equals(selectionReason) && resolverCtx.getSettings() != null) {
          GradleExecutionWorkspace executionWorkspace = resolverCtx.getSettings().getExecutionWorkspace();
          ModuleData moduleData = executionWorkspace.findModuleDataByArtifacts(projectDependency.getProjectDependencyArtifacts());
          if (moduleData != null) {
            projectDependencyInfos.add(new ProjectDependencyInfo(moduleData, null, projectDependency.getProjectDependencyArtifacts()));
          }
        }
        else {
          String moduleId = getModuleId(projectDependency);
          Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> projectPair = sourceSetMap.get(moduleId);
          if (projectPair == null) {
            MultiMap<Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>, File> projectPairs =
              new LinkedMultiMap<Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>, File>() {
                @Override
                protected EqualityPolicy<Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> getEqualityPolicy() {
                  //noinspection unchecked
                  return (EqualityPolicy<Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>>)EqualityPolicy.IDENTITY;
                }
              };

            for (File file: projectDependency.getProjectDependencyArtifacts()) {
              moduleId = artifactsMap.get(ExternalSystemApiUtil.toCanonicalPath(file.getAbsolutePath()));
              if (moduleId == null) continue;
              projectPair = sourceSetMap.get(moduleId);

              if (projectPair == null) continue;
              projectPairs.putValue(projectPair, file);
            }
            for (Map.Entry<Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>, Collection<File>> entry : projectPairs.entrySet()) {
              projectDependencyInfos.add(new ProjectDependencyInfo(
                entry.getKey().first.getData(), entry.getKey().second, entry.getValue()));
            }
          }
          else {
            projectDependencyInfos.add(new ProjectDependencyInfo(projectPair.first.getData(), projectPair.second,
                                                                 projectDependency.getProjectDependencyArtifacts()));
          }
        }

        if (projectDependencyInfos.isEmpty()) {
          final LibraryLevel level = LibraryLevel.MODULE;
          final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, "");
          LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
          libraryDependencyData.setScope(dependencyScope);
          libraryDependencyData.setOrder(mergedDependency.getClasspathOrder());
          libraryDependencyData.setExported(mergedDependency.getExported());

          if (!projectDependency.getProjectDependencyArtifacts().isEmpty()) {
            for (File artifact: projectDependency.getProjectDependencyArtifacts()) {
              library.addPath(LibraryPathType.BINARY, artifact.getPath());
            }
            depOwnerDataNode = ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
          }
          else {
            depOwnerDataNode = ownerDataNode;
          }
        }
        else {
          for (ProjectDependencyInfo projectDependencyInfo: projectDependencyInfos) {
            ModuleDependencyData moduleDependencyData = new ModuleDependencyData(ownerModule, projectDependencyInfo.myModuleData);
            moduleDependencyData.setScope(dependencyScope);
            if (projectDependencyInfo.mySourceSet != null && "test".equals(projectDependencyInfo.mySourceSet.getName())) {
              moduleDependencyData.setProductionOnTestDependency(true);
            }
            moduleDependencyData.setOrder(mergedDependency.getClasspathOrder());
            moduleDependencyData.setExported(mergedDependency.getExported());
            moduleDependencyData.setModuleDependencyArtifacts(ContainerUtil.map(projectDependencyInfo.dependencyArtifacts, File::getPath));
            depOwnerDataNode = ownerDataNode.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData);
          }

          // put transitive dependencies to the ownerDataNode,
          // since we can not determine from what project dependency artifact it was originated
          if (projectDependencyInfos.size() > 1) {
            depOwnerDataNode = ownerDataNode;
          }
        }
      }
      else if (mergedDependency instanceof ExternalLibraryDependency) {
        String libraryName = mergedDependency.getId().getPresentableName();
        final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName);
        library.setArtifactId(mergedDependency.getId().getName());
        library.setGroup(mergedDependency.getId().getGroup());
        library.setVersion(mergedDependency.getId().getVersion());

        library.addPath(LibraryPathType.BINARY, ((ExternalLibraryDependency)mergedDependency).getFile().getAbsolutePath());
        File sourcePath = ((ExternalLibraryDependency)mergedDependency).getSource();
        if (sourcePath != null) {
          library.addPath(LibraryPathType.SOURCE, sourcePath.getAbsolutePath());
        }
        File javaDocPath = ((ExternalLibraryDependency)mergedDependency).getJavadoc();
        if (javaDocPath != null) {
          library.addPath(LibraryPathType.DOC, javaDocPath.getAbsolutePath());
        }

        LibraryLevel level = StringUtil.isNotEmpty(libraryName) ? LibraryLevel.PROJECT : LibraryLevel.MODULE;
        if (StringUtil.isEmpty(libraryName) || !linkProjectLibrary(ideProject, library)) {
          level = LibraryLevel.MODULE;
        }

        LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
        libraryDependencyData.setScope(dependencyScope);
        libraryDependencyData.setOrder(mergedDependency.getClasspathOrder());
        libraryDependencyData.setExported(mergedDependency.getExported());
        depOwnerDataNode = ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
      }
      else if (mergedDependency instanceof ExternalMultiLibraryDependency) {
        final LibraryLevel level = LibraryLevel.MODULE;
        String libraryName = mergedDependency.getId().getPresentableName();
        final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName);
        library.setArtifactId(mergedDependency.getId().getName());
        library.setGroup(mergedDependency.getId().getGroup());
        library.setVersion(mergedDependency.getId().getVersion());
        LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
        libraryDependencyData.setScope(dependencyScope);
        libraryDependencyData.setOrder(mergedDependency.getClasspathOrder());
        libraryDependencyData.setExported(mergedDependency.getExported());

        for (File file: ((ExternalMultiLibraryDependency)mergedDependency).getFiles()) {
          library.addPath(LibraryPathType.BINARY, file.getAbsolutePath());
        }
        for (File file: ((ExternalMultiLibraryDependency)mergedDependency).getSources()) {
          library.addPath(LibraryPathType.SOURCE, file.getAbsolutePath());
        }
        for (File file: ((ExternalMultiLibraryDependency)mergedDependency).getJavadoc()) {
          library.addPath(LibraryPathType.DOC, file.getAbsolutePath());
        }

        depOwnerDataNode = ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
      }
      else if (mergedDependency instanceof FileCollectionDependency) {
        final LibraryLevel level = LibraryLevel.MODULE;
        String libraryName = "";
        final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName);
        LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
        libraryDependencyData.setScope(dependencyScope);
        libraryDependencyData.setOrder(mergedDependency.getClasspathOrder());
        libraryDependencyData.setExported(mergedDependency.getExported());

        for (File file: ((FileCollectionDependency)mergedDependency).getFiles()) {
          library.addPath(LibraryPathType.BINARY, file.getAbsolutePath());
        }

        ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
      }
      else if (mergedDependency instanceof UnresolvedExternalDependency) {
        String libraryName = mergedDependency.getId().getPresentableName();
        final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName, true);
        final String failureMessage = ((UnresolvedExternalDependency)mergedDependency).getFailureMessage();
        if (failureMessage != null) {
          library.addPath(LibraryPathType.BINARY, failureMessage);
        }
        LibraryLevel level = linkProjectLibrary(ideProject, library) ? LibraryLevel.PROJECT : LibraryLevel.MODULE;
        LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
        libraryDependencyData.setScope(dependencyScope);
        ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
      }

      if (depOwnerDataNode != null) {
        doBuildDependencies(resolverCtx, sourceSetMap, artifactsMap, mergedDependencyMap, depOwnerDataNode, dependency.getDependencies(),
                            ideProject);
      }
    }
  }

  public static boolean linkProjectLibrary(@Nullable DataNode<ProjectData> ideProject, @NotNull final LibraryData library) {
    if (ideProject == null) return false;

    String libraryName = library.getExternalName();
    DataNode<LibraryData> libraryData = ExternalSystemApiUtil.find(ideProject, ProjectKeys.LIBRARY,
                                                                   node -> libraryName.equals(node.getData().getExternalName()));
    if (libraryData == null) {
      ideProject.createChild(ProjectKeys.LIBRARY, library);
      return true;
    }
    return libraryData.getData().equals(library);
  }

  public static boolean isIdeaTask(final String taskName, @Nullable String group) {
    if ((group == null || "ide".equalsIgnoreCase(group)) && StringUtil.containsIgnoreCase(taskName, "idea")) return true;
    return "other".equalsIgnoreCase(group) && StringUtil.containsIgnoreCase(taskName, "idea");
  }

  @Nullable
  public static DataNode<ModuleData> findModule(@Nullable final DataNode<ProjectData> projectNode, @NotNull final String modulePath) {
    if (projectNode == null) return null;

    return ExternalSystemApiUtil.find(projectNode, ProjectKeys.MODULE,
                                      node -> node.getData().getLinkedExternalProjectPath().equals(modulePath));
  }

  @Nullable
  public static DataNode<ModuleData> findModuleById(@Nullable final DataNode<ProjectData> projectNode, @NotNull final String path) {
    if (projectNode == null) return null;
    return ExternalSystemApiUtil.find(projectNode, ProjectKeys.MODULE, node -> node.getData().getId().equals(path));
  }

  @Nullable
  public static DataNode<TaskData> findTask(@Nullable final DataNode<ProjectData> projectNode,
                                            @NotNull final String modulePath,
                                            @NotNull final String taskPath) {
    DataNode<ModuleData> moduleNode;
    final String taskName;
    if (StringUtil.startsWith(taskPath, ":")) {
      final int i = taskPath.lastIndexOf(':');
      moduleNode = i == 0 ? null : findModuleById(projectNode, taskPath.substring(0, i));
      if (moduleNode == null) {
        moduleNode = findModule(projectNode, modulePath);
      }
      taskName = (i + 1) <= taskPath.length() ? taskPath.substring(i + 1) : taskPath;
    }
    else {
      moduleNode = findModule(projectNode, modulePath);
      taskName = taskPath;
    }
    if (moduleNode == null) return null;

    return ExternalSystemApiUtil.find(moduleNode, ProjectKeys.TASK, node -> {
      String name = node.getData().getName();
      return name.equals(taskName) || name.equals(taskPath);
    });
  }
}