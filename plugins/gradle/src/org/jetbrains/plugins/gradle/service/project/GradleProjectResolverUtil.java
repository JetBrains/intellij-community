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

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId;
import org.jetbrains.plugins.gradle.ExternalDependencyId;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;

/**
 * @author Vladislav.Soroka
 * @since 10/6/2015
 */
public class GradleProjectResolverUtil {
  @NotNull
  private static final Key<Object> CONTAINER_KEY = Key.create(Object.class, ExternalSystemConstants.UNORDERED);

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
    if (dependencyScope == DependencyScope.TEST) {
      moduleId += ":test";
    }
    else {
      moduleId += ":main";
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

    DataNode fakeNode = new DataNode(CONTAINER_KEY, moduleDataNode.getData(), null);
    buildDependencies(sourceSetMap, fakeNode, dependencies, null);
    final Collection<DataNode<?>> dataNodes =
      ExternalSystemApiUtil.findAllRecursively(fakeNode, new BooleanFunction<DataNode<?>>() {
        @Override
        public boolean fun(DataNode<?> node) {
          return node.getData() instanceof DependencyData;
        }
      });
    return ContainerUtil.map(dataNodes, new Function<DataNode<?>, DependencyData>() {
      @Override
      public DependencyData fun(DataNode<?> node) {
        return (DependencyData)node.getData();
      }
    });
  }

  public static void buildDependencies(@NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
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

    doBuildDependencies(sourceSetMap, dependencyMap, ownerDataNode, dependencies, ideProject);
  }

  private static void doBuildDependencies(@NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
                                          @NotNull Map<ExternalDependencyId, ExternalDependency> mergedDependencyMap,
                                          @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                          @NotNull Collection<ExternalDependency> dependencies,
                                          @Nullable DataNode<ProjectData> ideProject) throws IllegalStateException {

    Map<ExternalDependencyId, ExternalDependency> dependencyMap = ContainerUtil.newLinkedHashMap();
    for (ExternalDependency dependency : dependencies) {
      dependencyMap.put(dependency.getId(), dependency);
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

      if (mergedDependency instanceof ExternalProjectDependency) {
        final ExternalProjectDependency projectDependency = (ExternalProjectDependency)mergedDependency;
        String moduleId = getModuleId(projectDependency);
        Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> projectPair = sourceSetMap.get(moduleId);
        ModuleDependencyData moduleDependencyData = new ModuleDependencyData(ownerModule, projectPair.first.getData());
        moduleDependencyData.setScope(dependencyScope);
        if ("test".equals(projectPair.second.getName())) {
          moduleDependencyData.setProductionOnTestDependency(true);
        }
        moduleDependencyData.setOrder(mergedDependency.getClasspathOrder());
        moduleDependencyData.setExported(mergedDependency.getExported());
        moduleDependencyData.setModuleDependencyArtifacts(projectDependency.getProjectDependencyArtifacts());

        DataNode<ModuleDependencyData> ideModuleDependencyNode =
          ownerDataNode.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData);
        doBuildDependencies(sourceSetMap, mergedDependencyMap, ideModuleDependencyNode, dependency.getDependencies(), ideProject);
      }
      if (mergedDependency instanceof ExternalLibraryDependency) {
        final LibraryLevel level = LibraryLevel.PROJECT;
        String libraryName = mergedDependency.getId().getPresentableName();
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
        DataNode<LibraryDependencyData> libraryDependencyDataNode =
          ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);

        linkProjectLibrary(ideProject, library);

        doBuildDependencies(sourceSetMap, mergedDependencyMap, libraryDependencyDataNode, dependency.getDependencies(), ideProject);
      }
      if (mergedDependency instanceof ExternalMultiLibraryDependency) {
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

        DataNode<LibraryDependencyData> libraryDependencyDataNode =
          ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);

        doBuildDependencies(sourceSetMap, mergedDependencyMap, libraryDependencyDataNode, dependency.getDependencies(), ideProject);
      }
      if (mergedDependency instanceof FileCollectionDependency) {
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
      if (mergedDependency instanceof UnresolvedExternalDependency) {
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
    }
  }

  public static void linkProjectLibrary(@Nullable DataNode<ProjectData> ideProject, @NotNull final LibraryData library) {
    if (ideProject == null) return;

    DataNode<LibraryData> libraryData =
      ExternalSystemApiUtil.find(ideProject, ProjectKeys.LIBRARY, new BooleanFunction<DataNode<LibraryData>>() {
        @Override
        public boolean fun(DataNode<LibraryData> node) {
          return library.equals(node.getData());
        }
      });
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

    return ExternalSystemApiUtil.find(projectNode, ProjectKeys.MODULE, new BooleanFunction<DataNode<ModuleData>>() {
      @Override
      public boolean fun(DataNode<ModuleData> node) {
        return node.getData().getLinkedExternalProjectPath().equals(modulePath);
      }
    });
  }

  @Nullable
  public static DataNode<TaskData> findTask(@Nullable final DataNode<ProjectData> projectNode,
                                            @NotNull final String modulePath,
                                            @NotNull final String taskName) {
    final DataNode<ModuleData> moduleNode = findModule(projectNode, modulePath);
    if (moduleNode == null) return null;

    return ExternalSystemApiUtil.find(moduleNode, ProjectKeys.TASK, new BooleanFunction<DataNode<TaskData>>() {
      @Override
      public boolean fun(DataNode<TaskData> node) {
        return node.getData().getName().equals(taskName);
      }
    });
  }
}