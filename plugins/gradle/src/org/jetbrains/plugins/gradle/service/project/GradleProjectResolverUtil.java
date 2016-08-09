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
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
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
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;

import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.CONFIGURATION_ARTIFACTS;

/**
 * @author Vladislav.Soroka
 * @since 10/6/2015
 */
public class GradleProjectResolverUtil {
  private static final Logger LOG = Logger.getInstance(GradleProjectResolverUtil.class);
  @NotNull
  private static final Key<Object> CONTAINER_KEY = Key.create(Object.class, ExternalSystemConstants.UNORDERED);

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
      ideProjectPath == null ? mainModuleConfigPath : ideProjectPath + "/.idea/modules/" +
                                                      (relativePath == null || relativePath.equals(".") ? "" : relativePath);
    if (ExternalSystemDebugEnvironment.DEBUG_ORPHAN_MODULES_PROCESSING) {
      LOG.info(String.format(
        "Creating module data ('%s') with the external config path: '%s'", gradleModule.getGradleProject().getPath(), mainModuleConfigPath
      ));
    }

    String gradlePath = gradleModule.getGradleProject().getPath();
    final boolean isRootModule = StringUtil.isEmpty(gradlePath) || ":".equals(gradlePath);
    String mainModuleId = isRootModule ? moduleName : gradlePath;
    final ModuleData moduleData =
      new ModuleData(mainModuleId, GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), moduleName,
                     mainModuleFileDirectoryPath, mainModuleConfigPath);

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (externalProject != null) {
      moduleData.setGroup(externalProject.getGroup());
      moduleData.setVersion(externalProject.getVersion());
      moduleData.setDescription(externalProject.getDescription());
      moduleData.setArtifacts(externalProject.getArtifacts());
    }

    return projectDataNode.createChild(ProjectKeys.MODULE, moduleData);
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
    for (BasicGradleProject project : build.getProjects()) {
      if (project.getPath().equals(path)) {
        return project.getProjectDirectory();
      }
    }
    return null;
  }

  @NotNull
  public static String getModuleId(@NotNull IdeaModule gradleModule) {
    GradleProject gradleProject = gradleModule.getGradleProject();
    return getModuleId(gradleProject.getPath(), gradleProject.getName());
  }

  @NotNull
  public static String getModuleId(@NotNull ExternalProject externalProject) {
    return getModuleId(externalProject.getQName(), externalProject.getName());
  }

  @NotNull
  public static String getModuleId(String gradlePath, String moduleName) {
    return StringUtil.isEmpty(gradlePath) || ":".equals(gradlePath) ? moduleName : gradlePath;
  }

  @NotNull
  public static String getModuleId(@NotNull ExternalProject externalProject, @NotNull ExternalSourceSet sourceSet) {
    String mainModuleId = getModuleId(externalProject);
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

  @NotNull
  public static DependencyScope getDependencyScope(@Nullable String scope) {
    return scope != null ? DependencyScope.valueOf(scope) : DependencyScope.COMPILE;
  }

  public static void attachGradleSdkSources(@NotNull final IdeaModule gradleModule,
                                            @Nullable final File libFile,
                                            @NotNull final LibraryData library,
                                            @NotNull final ProjectResolverContext resolverCtx) {
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
    if (!FileUtil.isAncestor(gradleHomeDir, libFile, true)) return;

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

  @SuppressWarnings("unchecked")
  public static Collection<DependencyData> getIdeDependencies(@NotNull DataNode<? extends ModuleData> moduleDataNode,
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
    buildDependencies(sourceSetMap, artifactsMap, fakeNode, dependencies, null);
    final Collection<DataNode<?>> dataNodes =
      ExternalSystemApiUtil.findAllRecursively(fakeNode, node -> node.getData() instanceof DependencyData);
    return ContainerUtil.map(dataNodes, node -> (DependencyData)node.getData());
  }

  public static void buildDependencies(@NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
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

    doBuildDependencies(sourceSetMap, artifactsMap, dependencyMap, ownerDataNode, dependencies, ideProject);
  }

  private static void doBuildDependencies(@NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
                                          @NotNull final Map<String, String> artifactsMap,
                                          @NotNull Map<ExternalDependencyId, ExternalDependency> mergedDependencyMap,
                                          @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                          @NotNull Collection<ExternalDependency> dependencies,
                                          @Nullable DataNode<ProjectData> ideProject) throws IllegalStateException {

    Map<ExternalDependencyId, ExternalDependency> dependencyMap = ContainerUtil.newLinkedHashMap();
    for (ExternalDependency dependency : dependencies) {
      final ExternalDependency dep = dependencyMap.get(dependency.getId());
      if (dep instanceof AbstractExternalDependency) {
        dep.getDependencies().addAll(ContainerUtil.subtract(dependency.getDependencies(), dep.getDependencies()));
      }
      else {
        dependencyMap.put(dependency.getId(), dependency);
      }
    }

    for (ExternalDependency dependency : dependencyMap.values()) {
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
          DataNode<GradleSourceSetData> dataNode;
          ExternalSourceSet sourceSet;
          Collection<File> dependencyArtifacts;

          public ProjectDependencyInfo(Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> pair, Collection<File> dependencyArtifacts) {
            this.dataNode = pair.first;
            this.sourceSet = pair.second;
            this.dependencyArtifacts = dependencyArtifacts;
          }
        }

        final ExternalProjectDependency projectDependency = (ExternalProjectDependency)mergedDependency;
        String moduleId = getModuleId(projectDependency);
        Collection<ProjectDependencyInfo> projectDependencyInfos = ContainerUtil.newArrayList();

        Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> projectPair = sourceSetMap.get(moduleId);
        if (projectPair == null) {
          for (File file : projectDependency.getProjectDependencyArtifacts()) {
            moduleId = artifactsMap.get(ExternalSystemApiUtil.toCanonicalPath(file.getAbsolutePath()));
            if (moduleId != null) {
              projectPair = sourceSetMap.get(moduleId);
              if (projectPair != null) {
                projectDependencyInfos.add(new ProjectDependencyInfo(projectPair, Collections.singleton(file)));
              }
            }
          }
        }
        else {
          projectDependencyInfos.add(new ProjectDependencyInfo(projectPair, projectDependency.getProjectDependencyArtifacts()));
        }

        if (projectDependencyInfos.isEmpty()) {
          final LibraryLevel level = LibraryLevel.MODULE;
          final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, "");
          LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
          libraryDependencyData.setScope(dependencyScope);
          libraryDependencyData.setOrder(mergedDependency.getClasspathOrder());
          libraryDependencyData.setExported(mergedDependency.getExported());

          if (!projectDependency.getProjectDependencyArtifacts().isEmpty()) {
            for (File artifact : projectDependency.getProjectDependencyArtifacts()) {
              library.addPath(LibraryPathType.BINARY, artifact.getPath());
            }
            depOwnerDataNode = ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
          } else {
            depOwnerDataNode = ownerDataNode;
          }
        }
        else {
          for (ProjectDependencyInfo projectDependencyInfo : projectDependencyInfos) {
            ModuleDependencyData moduleDependencyData = new ModuleDependencyData(ownerModule, projectDependencyInfo.dataNode.getData());
            moduleDependencyData.setScope(dependencyScope);
            if ("test".equals(projectDependencyInfo.sourceSet.getName())) {
              moduleDependencyData.setProductionOnTestDependency(true);
            }
            moduleDependencyData.setOrder(mergedDependency.getClasspathOrder());
            moduleDependencyData.setExported(mergedDependency.getExported());
            moduleDependencyData.setModuleDependencyArtifacts(ContainerUtil.map(projectDependencyInfo.dependencyArtifacts, File::getPath));
            depOwnerDataNode = ownerDataNode.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData);
          }

          // put transitive dependencies to the ownerDataNode,
          // since we can not determine from what project dependency artifact it was originated
          if(projectDependencyInfos.size() > 1) {
            depOwnerDataNode = ownerDataNode;
          }
        }
      }
      else if (mergedDependency instanceof ExternalLibraryDependency) {
        String libraryName = mergedDependency.getId().getPresentableName();
        final LibraryLevel level = StringUtil.isNotEmpty(libraryName) ? LibraryLevel.PROJECT : LibraryLevel.MODULE;
        final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName);
        LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
        libraryDependencyData.setScope(dependencyScope);
        libraryDependencyData.setOrder(mergedDependency.getClasspathOrder());
        libraryDependencyData.setExported(mergedDependency.getExported());

        library.addPath(LibraryPathType.BINARY, ((ExternalLibraryDependency)mergedDependency).getFile().getAbsolutePath());
        File sourcePath = ((ExternalLibraryDependency)mergedDependency).getSource();
        if (sourcePath != null) {
          library.addPath(LibraryPathType.SOURCE, sourcePath.getAbsolutePath());
        }
        File javaDocPath = ((ExternalLibraryDependency)mergedDependency).getJavadoc();
        if (javaDocPath != null) {
          library.addPath(LibraryPathType.DOC, javaDocPath.getAbsolutePath());
        }
        depOwnerDataNode = ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);

        if (StringUtil.isNotEmpty(libraryName)) {
          linkProjectLibrary(ideProject, library);
        }
      }
      else if (mergedDependency instanceof ExternalMultiLibraryDependency) {
        final LibraryLevel level = LibraryLevel.MODULE;
        String libraryName = mergedDependency.getId().getPresentableName();
        final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName);
        LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
        libraryDependencyData.setScope(dependencyScope);
        libraryDependencyData.setOrder(mergedDependency.getClasspathOrder());
        libraryDependencyData.setExported(mergedDependency.getExported());

        for (File file : ((ExternalMultiLibraryDependency)mergedDependency).getFiles()) {
          library.addPath(LibraryPathType.BINARY, file.getAbsolutePath());
        }
        for (File file : ((ExternalMultiLibraryDependency)mergedDependency).getSources()) {
          library.addPath(LibraryPathType.SOURCE, file.getAbsolutePath());
        }
        for (File file : ((ExternalMultiLibraryDependency)mergedDependency).getJavadoc()) {
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

        for (File file : ((FileCollectionDependency)mergedDependency).getFiles()) {
          library.addPath(LibraryPathType.BINARY, file.getAbsolutePath());
        }

        ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
      }
      else if (mergedDependency instanceof UnresolvedExternalDependency) {
        final LibraryLevel level = LibraryLevel.PROJECT;
        String libraryName = mergedDependency.getId().getPresentableName();
        final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName, true);
        LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
        libraryDependencyData.setScope(dependencyScope);
        final String failureMessage = ((UnresolvedExternalDependency)mergedDependency).getFailureMessage();
        if (failureMessage != null) {
          library.addPath(LibraryPathType.BINARY, failureMessage);
        }
        ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
        linkProjectLibrary(ideProject, library);
      }

      if (depOwnerDataNode != null) {
        doBuildDependencies(sourceSetMap, artifactsMap, mergedDependencyMap, depOwnerDataNode, dependency.getDependencies(), ideProject);
      }
    }
  }

  public static void linkProjectLibrary(@Nullable DataNode<ProjectData> ideProject, @NotNull final LibraryData library) {
    if (ideProject == null) return;

    DataNode<LibraryData> libraryData =
      ExternalSystemApiUtil.find(ideProject, ProjectKeys.LIBRARY, node -> library.equals(node.getData()));
    if (libraryData == null) {
      ideProject.createChild(ProjectKeys.LIBRARY, library);
    }
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
      String path = taskPath.substring(0, i);
      moduleNode = findModuleById(projectNode, path);
      if (moduleNode == null || !FileUtil.isAncestor(moduleNode.getData().getLinkedExternalProjectPath(), modulePath, false)) {
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