// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemDebugEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import org.gradle.api.artifacts.Dependency;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId;
import org.jetbrains.plugins.gradle.ExternalDependencyId;
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencySyncIssue;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleDataKt;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Vladislav.Soroka
 */
public final class GradleProjectResolverUtil {
  private static final Logger LOG = Logger.getInstance(GradleProjectResolverUtil.class);
  @NonNls private static final String SOURCE_JAR_SUFFIX = "-sources.jar";
  @NonNls private static final String JAVADOC_JAR_SUFFIX = "-javadoc.jar";

  @NotNull
  public static DataNode<ModuleData> createMainModule(@NotNull ProjectResolverContext resolverCtx,
                                                      @NotNull IdeaModule gradleModule,
                                                      @NotNull DataNode<ProjectData> projectDataNode) {
    GradleProject gradleProject = gradleModule.getGradleProject();
    final String moduleName = resolverCtx.isUseQualifiedModuleNames()
                              ? gradleProject.getName()
                              : gradleModule.getName();

    if (moduleName == null) {
      throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
    }

    final ProjectData projectData = projectDataNode.getData();
    final String mainModuleConfigPath = getModuleConfigPath(resolverCtx, gradleModule, projectData.getLinkedExternalProjectPath());
    final String ideProjectPath = resolverCtx.getIdeProjectPath();
    final String relativePath;
    boolean isUnderProjectRoot = FileUtil.isAncestor(projectData.getLinkedExternalProjectPath(), mainModuleConfigPath, false);
    if (isUnderProjectRoot) {
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
        "Creating module data ('%s') with the external config path: '%s'", gradleProject.getPath(), mainModuleConfigPath
      ));
    }

    String mainModuleId = getModuleId(resolverCtx, gradleModule);
    final ModuleData moduleData =
      new ModuleData(mainModuleId, GradleConstants.SYSTEM_ID, getDefaultModuleTypeId(), moduleName,
                     mainModuleFileDirectoryPath, mainModuleConfigPath);

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (externalProject != null) {
      GradleModuleDataKt.setGradlePath(moduleData, externalProject.getPath());
      GradleModuleDataKt.setGradleIdentityPath(moduleData, getIdentityPath(resolverCtx, externalProject, projectData.getExternalName()));
      moduleData.setInternalName(getInternalModuleName(gradleModule, externalProject, resolverCtx));
      moduleData.setGroup(externalProject.getGroup());
      moduleData.setVersion(externalProject.getVersion());
      moduleData.setDescription(externalProject.getDescription());
      moduleData.setModuleName(moduleName);
      if (!resolverCtx.isResolveModulePerSourceSet()) {
        moduleData.setArtifacts(externalProject.getArtifacts());
        moduleData.setPublication(new ProjectId(externalProject.getGroup(),
                                                externalProject.getName(),
                                                externalProject.getVersion()));
      }
    }

    File rootDir = gradleProject.getProjectIdentifier().getBuildIdentifier().getRootDir();
    String rootProjectPath = ExternalSystemApiUtil.toCanonicalPath(rootDir.getPath());
    boolean isComposite = !resolverCtx.getModels().getIncludedBuilds().isEmpty();
    boolean isIncludedBuildTaskRunningSupported = isComposite && isIncludedBuildTaskRunningSupported(projectDataNode, resolverCtx);
    File mainBuildRootDir = resolverCtx.getModels().getMainBuild().getBuildIdentifier().getRootDir();
    String mainBuildRootPath = ExternalSystemApiUtil.toCanonicalPath(mainBuildRootDir.getPath());
    boolean isFromIncludedBuild = !rootProjectPath.equals(mainBuildRootPath);

    boolean useIncludedBuildPathPrefix = isFromIncludedBuild && isIncludedBuildTaskRunningSupported;
    String compositeBuildGradlePath = useIncludedBuildPathPrefix ? ":" + getRootProject(gradleProject).getName() : "";
    GradleModuleDataKt.setIncludedBuild(moduleData, isFromIncludedBuild);

    String directoryToRunTask;
    if (compositeBuildGradlePath.isEmpty()) {
      directoryToRunTask = isUnderProjectRoot ? mainModuleConfigPath : rootProjectPath;
    }
    else {
      directoryToRunTask = mainBuildRootPath;
    }
    GradleModuleDataKt.setDirectoryToRunTask(moduleData, directoryToRunTask);
    return projectDataNode.createChild(ProjectKeys.MODULE, moduleData);
  }

  @NotNull
  private static String getIdentityPath(@NotNull ProjectResolverContext resolverCtx,
                                        @NotNull ExternalProject externalProject,
                                        @NotNull @NlsSafe String rootProjectName) {
    String buildSrcPrefix = getBuildSrcPrefix(resolverCtx, rootProjectName);
    if (!StringUtil.isEmpty(buildSrcPrefix)) {
      return patchBuildSrcIdentity(buildSrcPrefix, externalProject.getIdentityPath());
    }
    return externalProject.getIdentityPath();
  }

  @Nullable
  private static String getBuildSrcPrefix(@NotNull ProjectResolverContext ctx, @NotNull String rootProjectName) {
    if (ctx.getBuildSrcGroup() == null) {
      return null;
    }
    return ":" + ctx.getBuildSrcGroup() + ":buildSrc";
  }

  private static String patchBuildSrcIdentity(@NotNull String prefix, @NotNull String path) {
    return path.isEmpty() || path.equals(":") ? prefix : prefix + path;
  }


  @NotNull
  private static GradleProject getRootProject(@NotNull GradleProject project) {
    GradleProject result = project;
    while (result.getParent() != null) {
      result = result.getParent();
    }
    return result;
  }

  private static final Key<Boolean> IS_INCLUDED_BUILD_TASK_RUN_SUPPORTED = Key.create("is included build task running supported");
  private static boolean isIncludedBuildTaskRunningSupported(@NotNull DataNode<ProjectData> project,
                                                             @NotNull ProjectResolverContext resolverCtx) {
    Boolean isSupported = IS_INCLUDED_BUILD_TASK_RUN_SUPPORTED.get(project);
    if (isSupported == null) {
      String gradleVersion = resolverCtx.getProjectGradleVersion();
      isSupported = gradleVersion != null && GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("6.8")) >= 0;
      IS_INCLUDED_BUILD_TASK_RUN_SUPPORTED.set(project, isSupported);
    }
    return isSupported;
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
    String buildSrcGroup = resolverCtx.getBuildSrcGroup(gradleModule);
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
    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (externalProject != null) {
      File projectDir = externalProject.getProjectDir();
      return ExternalSystemApiUtil.toCanonicalPath(projectDir.getPath());
    }
    return GradleUtil.getConfigPath(gradleModule.getGradleProject(), rootProjectPath);
  }

  @NotNull
  public static String getModuleId(@NotNull ProjectResolverContext resolverCtx, @NotNull IdeaModule gradleModule) {
    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (externalProject == null) {
      throw new IllegalStateException(
        "Missing " + ExternalProject.class.getSimpleName() + " for " + gradleModule.getGradleProject().getPath()
      );
    }
    if (!StringUtil.isEmpty(resolverCtx.getBuildSrcGroup())) {
      return resolverCtx.getBuildSrcGroup() + ":" + getModuleId(externalProject);
    }
    return getModuleId(externalProject);
  }


  @NotNull
  public static String getModuleId(String gradlePath, String moduleName) {
    return StringUtil.isEmpty(gradlePath) || ":".equals(gradlePath) ? moduleName : gradlePath;
  }

  @NotNull
  public static String getModuleId(@NotNull ExternalProject externalProject) {
    return getModuleId(externalProject.getIdentityPath(), externalProject.getName());
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

  /**
   * @deprecated Use getGradleIdentityPathOrNull instead
   */
  @Deprecated
  @Nullable
  public static String getGradlePath(final Module module) {
    return getGradleIdentityPathOrNull(module);
  }

  @Nullable
  public static String getGradleIdentityPathOrNull(final Module module) {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return null;
    final String externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module);
    if (externalProjectId == null) return null;

    final String moduleType = ExternalSystemApiUtil.getExternalModuleType(module);
    boolean trimSourceSet = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(moduleType);
    List<String> pathParts = StringUtil.split(externalProjectId, ":");
    if (!externalProjectId.startsWith(":") && !pathParts.isEmpty()) {
      pathParts = pathParts.subList(1, pathParts.size());
    }
    if (trimSourceSet && !pathParts.isEmpty()) {
      pathParts = pathParts.subList(0, pathParts.size() - 1);
    }
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
        for (File srcRoot : gradleSrcRoots) {
          library.addPath(LibraryPathType.SOURCE, srcRoot.getPath());
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
        library.addPath(LibraryPathType.SOURCE, srcDir.getPath());
      }
    }
  }

  public static void attachSourcesAndJavadocFromGradleCacheIfNeeded(File gradleUserHomeDir, LibraryData libraryData) {
    attachSourcesAndJavadocFromGradleCacheIfNeeded(null, gradleUserHomeDir, libraryData);
  }

  private static final Key<Set<LibraryData>> LIBRARIES_CACHE =
    Key.create("GradleProjectResolverUtil.LIBRARIES_CACHE");
  private static final Key<Map<String, Map<LibraryPathType, List<String>>>> PATHS_CACHE =
    Key.create("GradleProjectResolverUtil.PATHS_CACHE");

  public static void attachSourcesAndJavadocFromGradleCacheIfNeeded(ProjectResolverContext context,
                                                                    File gradleUserHomeDir,
                                                                    LibraryData libraryData) {
    boolean sourceResolved = !libraryData.getPaths(LibraryPathType.SOURCE).isEmpty();
    boolean docResolved = !libraryData.getPaths(LibraryPathType.DOC).isEmpty();
    if (sourceResolved && docResolved) {
      return;
    }

    Map<String, Map<LibraryPathType, List<String>>> pathsCache = null;
    if (context != null) {
      // skip already processed libraries
      Set<LibraryData> libsCache = context.getUserData(LIBRARIES_CACHE);
      if (libsCache == null) {
        libsCache = context.putUserDataIfAbsent(LIBRARIES_CACHE, Collections.newSetFromMap(new IdentityHashMap<>()));
      }
      if (!libsCache.add(libraryData)) return;

      pathsCache = context.getUserData(PATHS_CACHE);
      if (pathsCache == null) pathsCache = context.putUserDataIfAbsent(PATHS_CACHE, new HashMap<>());
    }

    for (String path : libraryData.getPaths(LibraryPathType.BINARY)) {
      if (!FileUtil.isAncestor(gradleUserHomeDir.getPath(), path, true)) continue;

      // take already processed paths from cache
      Map<LibraryPathType, List<String>> collectedPaths = pathsCache == null ? null : pathsCache.get(path);
      if (collectedPaths == null) {
        collectedPaths = new HashMap<>();
        if (pathsCache != null) {
          pathsCache.put(path, collectedPaths);
        }
        collectSourcesAndJavadocsFor(path, collectedPaths, sourceResolved, docResolved);
      }

      for (Map.Entry<LibraryPathType, List<String>> each : collectedPaths.entrySet()) {
        for (String cachedPath : each.getValue()) {
          libraryData.addPath(each.getKey(), cachedPath);
        }
      }
    }
  }

  private static void collectSourcesAndJavadocsFor(@NonNls @NotNull String binaryPath,
                                                   @NotNull Map<LibraryPathType, List<String>> collect,
                                                   boolean sourceResolved, boolean docResolved) {
    if (sourceResolved && docResolved) {
      return;
    }
    try {
      if (binaryPath.contains("/.gradle/caches/modules-2/files-2.1/")) {
        collectSourcesAndJavadocsFromGradleCache(binaryPath, collect, sourceResolved, docResolved);
      }
      else {
        collectSourcesAndJavadocsFromTheSameFolder(binaryPath, collect, sourceResolved, docResolved);
      }
    }
    catch (IOException | InvalidPathException e) {
      LOG.debug(e);
    }
  }

  private static void collectSourcesAndJavadocsFromGradleCache(@NotNull String binaryPath,
                                                               @NotNull Map<LibraryPathType, List<String>> collect,
                                                               boolean sourceResolved,
                                                               boolean docResolved) throws IOException {
    final Path file = Paths.get(binaryPath);
    Path binaryFileParent = file.getParent();
    Path grandParentFile = binaryFileParent.getParent();

    final boolean[] sourceFound = {sourceResolved};
    final boolean[] docFound = {docResolved};

    Files.walkFileTree(grandParentFile, EnumSet.noneOf(FileVisitOption.class), 2, new SimpleFileVisitor<>() {
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
          String candidateFileName = sourceCandidate.getFileName().toString();
          if (!sourceFound[0] && StringUtil.endsWith(candidateFileName, SOURCE_JAR_SUFFIX)) {
            collect.computeIfAbsent(LibraryPathType.SOURCE, type -> new SmartList<>())
              .add(sourceCandidate.toFile().getPath());
            sourceFound[0] = true;
          }
          else if (!docFound[0] && StringUtil.endsWith(candidateFileName, JAVADOC_JAR_SUFFIX)) {
            collect.computeIfAbsent(LibraryPathType.DOC, type -> new SmartList<>())
              .add(sourceCandidate.toFile().getPath());
            docFound[0] = true;
          }
        }
        if (sourceFound[0] && docFound[0]) {
          return FileVisitResult.TERMINATE;
        }
        return super.visitFile(sourceCandidate, attrs);
      }
    });
  }

  private static void collectSourcesAndJavadocsFromTheSameFolder(@NotNull String binaryPath,
                                                                 @NotNull Map<LibraryPathType, List<String>> collect,
                                                                 boolean sourceResolved,
                                                                 boolean docResolved) throws IOException {
    final Path file = Paths.get(binaryPath);
    Path binaryFileParent = file.getParent();
    if (!Files.isDirectory(binaryFileParent)) return;
    try (Stream<Path> list = Files.list(binaryFileParent)) {
      for (Iterator<Path> it = list.iterator(); it.hasNext(); ) {
        Path p = it.next();
        if (!Files.isRegularFile(p)) continue;

        String name = p.getFileName().toString();
        if (!sourceResolved && name.endsWith(SOURCE_JAR_SUFFIX)) {
          collect.computeIfAbsent(LibraryPathType.SOURCE, type -> new SmartList<>()).add(p.toFile().getPath());
          sourceResolved = true;
        }
        else if (!docResolved && name.endsWith(JAVADOC_JAR_SUFFIX)) {
          collect.computeIfAbsent(LibraryPathType.DOC, type -> new SmartList<>()).add(p.toFile().getPath());
          docResolved = true;
        }
        if (sourceResolved && docResolved) {
          return;
        }
      }
    }
  }

  public static void buildDependencies(@NotNull ProjectResolverContext resolverCtx,
                                       @NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
                                       @NotNull final Map<String, String> artifactsMap,
                                       @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                       @NotNull Collection<ExternalDependency> dependencies,
                                       @Nullable DataNode<ProjectData> ideProject) throws IllegalStateException {
    Map<ExternalDependencyId, ExternalDependency> dependencyMap = new HashMap<>();

    Queue<ExternalDependency> queue = new ArrayDeque<>(dependencies);
    while (!queue.isEmpty()) {
      final ExternalDependency dependency = queue.remove();
      DefaultExternalDependencyId key = new DefaultExternalDependencyId(dependency.getId());
      ExternalDependency seenDependency = dependencyMap.get(key);
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

            ContainerUtil.addIfNotNull(mergedDependency.getFiles(), ((ExternalLibraryDependency)seenDependency).getFile());
            ContainerUtil.addIfNotNull(mergedDependency.getFiles(), ((ExternalLibraryDependency)dependency).getFile());
            ContainerUtil.addIfNotNull(mergedDependency.getSources(), ((ExternalLibraryDependency)seenDependency).getSource());
            ContainerUtil.addIfNotNull(mergedDependency.getSources(), ((ExternalLibraryDependency)dependency).getSource());
            ContainerUtil.addIfNotNull(mergedDependency.getJavadoc(), ((ExternalLibraryDependency)seenDependency).getJavadoc());
            ContainerUtil.addIfNotNull(mergedDependency.getJavadoc(), ((ExternalLibraryDependency)dependency).getJavadoc());

            dependencyMap.put(dependency.getId(), mergedDependency);
            continue;
          }
          else if (seenDependency instanceof DefaultExternalMultiLibraryDependency) {
            DefaultExternalMultiLibraryDependency mergedDependency = (DefaultExternalMultiLibraryDependency)seenDependency;
            ContainerUtil.addIfNotNull(mergedDependency.getFiles(), ((ExternalLibraryDependency)dependency).getFile());
            ContainerUtil.addIfNotNull(mergedDependency.getSources(), ((ExternalLibraryDependency)dependency).getSource());
            ContainerUtil.addIfNotNull(mergedDependency.getJavadoc(), ((ExternalLibraryDependency)dependency).getJavadoc());
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

      dependencyMap.put(key, dependency);
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
    int classpathOrderShift = 0;
    Map<ExternalDependencyId, ExternalDependency> dependencyMap = new LinkedHashMap<>();
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
      Collection<ExternalDependency> transitiveDependencies = dependency.getDependencies();
      final ExternalDependency mergedDependency = ContainerUtil.getOrElse(mergedDependencyMap, dependency.getId(), dependency);
      DependencyScope dependencyScope = getDependencyScope(mergedDependency.getScope());

      ModuleData ownerModule = null;
      if (ownerDataNode.getData() instanceof ModuleData) {
        ownerModule = (ModuleData)ownerDataNode.getData();
      }
      else if (ownerDataNode.getData() instanceof DependencyData) {
        ownerModule = ((DependencyData<?>)ownerDataNode.getData()).getOwnerModule();
      }

      assert ownerModule != null;

      DataNode<? extends ExternalEntityData> depOwnerDataNode = null;
      if (mergedDependency instanceof ExternalProjectDependency projectDependency) {

        Collection<ProjectDependencyInfo> projectDependencyInfos = new ArrayList<>();
        if (resolverCtx.getSettings() != null) {
          GradleExecutionWorkspace executionWorkspace = resolverCtx.getSettings().getExecutionWorkspace();
          ModuleData moduleData = executionWorkspace.findModuleDataByArtifacts(projectDependency.getProjectDependencyArtifacts());
          if (moduleData != null) {
            projectDependencyInfos.add(new ProjectDependencyInfo(moduleData, null, projectDependency.getProjectDependencyArtifacts()));
          }
        }
        if (projectDependencyInfos.isEmpty()) {
          String moduleId;
          Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> projectPair;
          MultiMap<Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>, File> projectPairs =
            new MultiMap<>(new Reference2ObjectLinkedOpenHashMap<>());

          for (File file : projectDependency.getProjectDependencyArtifacts()) {
            moduleId = artifactsMap.get(ExternalSystemApiUtil.toCanonicalPath(file.getPath()));
            if (moduleId == null) continue;
            projectPair = sourceSetMap.get(moduleId);

            if (projectPair == null) continue;
            projectPairs.putValue(projectPair, file);
          }
          for (Map.Entry<Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>, Collection<File>> entry : projectPairs.entrySet()) {
            projectDependencyInfos.add(new ProjectDependencyInfo(
              entry.getKey().first.getData(), entry.getKey().second, entry.getValue()));
          }

          String moduleIdFromDependency = getModuleId(projectDependency);
          Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> projectPairFromMap = sourceSetMap.get(moduleIdFromDependency);
          if (projectPairFromMap != null) {
            if (doesNotContainDependencyOn(projectDependencyInfos, projectPairFromMap.first.getData())) {
              final Collection<File> artifacts = projectDependency.getProjectDependencyArtifacts();
              artifacts.removeAll(collectProcessedArtifacts(projectDependencyInfos));
              projectDependencyInfos.add(new ProjectDependencyInfo(projectPairFromMap.first.getData(),
                                                                   projectPairFromMap.second,
                                                                   artifacts));
            }
          }
        }

        if (projectDependencyInfos.isEmpty()) {
          final LibraryLevel level = LibraryLevel.MODULE;
          final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, "");
          LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
          libraryDependencyData.setScope(dependencyScope);
          libraryDependencyData.setOrder(mergedDependency.getClasspathOrder() + classpathOrderShift);
          libraryDependencyData.setExported(mergedDependency.getExported());

          if (!projectDependency.getProjectDependencyArtifacts().isEmpty()) {
            for (File artifact : projectDependency.getProjectDependencyArtifacts()) {
              library.addPath(LibraryPathType.BINARY, artifact.getPath());
            }
            depOwnerDataNode = ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
          }
          else {
            depOwnerDataNode = ownerDataNode;
          }
        }
        else {
          int i = 0;
          for (ProjectDependencyInfo projectDependencyInfo : projectDependencyInfos) {
            if (i++ > 0 && transitiveDependencies.isEmpty()) {
              classpathOrderShift++;
            }
            ModuleDependencyData moduleDependencyData = new ModuleDependencyData(ownerModule, projectDependencyInfo.myModuleData);
            moduleDependencyData.setScope(dependencyScope);
            if (projectDependencyInfo.mySourceSet != null && isTestSourceSet(projectDependencyInfo.mySourceSet)) {
              moduleDependencyData.setProductionOnTestDependency(true);
            }
            moduleDependencyData.setOrder(mergedDependency.getClasspathOrder() + classpathOrderShift);
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
        LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName);
        library.setArtifactId(mergedDependency.getId().getName());
        library.setGroup(mergedDependency.getId().getGroup());
        library.setVersion(mergedDependency.getId().getVersion());

        library.addPath(LibraryPathType.BINARY, ((ExternalLibraryDependency)mergedDependency).getFile().getPath());
        File sourcePath = ((ExternalLibraryDependency)mergedDependency).getSource();
        if (sourcePath != null) {
          library.addPath(LibraryPathType.SOURCE, sourcePath.getPath());
        }
        File javaDocPath = ((ExternalLibraryDependency)mergedDependency).getJavadoc();
        if (javaDocPath != null) {
          library.addPath(LibraryPathType.DOC, javaDocPath.getPath());
        }

        LibraryLevel level = StringUtil.isNotEmpty(libraryName) ? LibraryLevel.PROJECT : LibraryLevel.MODULE;
        if (StringUtil.isEmpty(libraryName) || !linkProjectLibrary(ideProject, library)) {
          level = LibraryLevel.MODULE;
        }

        LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
        libraryDependencyData.setScope(dependencyScope);
        libraryDependencyData.setOrder(mergedDependency.getClasspathOrder() + classpathOrderShift);
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
        libraryDependencyData.setOrder(mergedDependency.getClasspathOrder() + classpathOrderShift);
        libraryDependencyData.setExported(mergedDependency.getExported());

        for (File file : ((ExternalMultiLibraryDependency)mergedDependency).getFiles()) {
          library.addPath(LibraryPathType.BINARY, file.getPath());
        }
        for (File file : ((ExternalMultiLibraryDependency)mergedDependency).getSources()) {
          library.addPath(LibraryPathType.SOURCE, file.getPath());
        }
        for (File file : ((ExternalMultiLibraryDependency)mergedDependency).getJavadoc()) {
          library.addPath(LibraryPathType.DOC, file.getPath());
        }

        depOwnerDataNode = ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
      }
      else if (mergedDependency instanceof FileCollectionDependency) {
        final LibraryLevel level = LibraryLevel.MODULE;
        String libraryName = "";
        final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName);
        LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
        libraryDependencyData.setScope(dependencyScope);
        libraryDependencyData.setOrder(mergedDependency.getClasspathOrder() + classpathOrderShift);
        libraryDependencyData.setExported(mergedDependency.getExported());

        for (File file : ((FileCollectionDependency)mergedDependency).getFiles()) {
          library.addPath(LibraryPathType.BINARY, file.getPath());
          if (mergedDependency instanceof DefaultFileCollectionDependency &&
              ((DefaultFileCollectionDependency)mergedDependency).isExcludedFromIndexing()) {
            library.addPath(LibraryPathType.EXCLUDED, file.getPath());
          }
        }

        ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
      }
      else if (mergedDependency instanceof UnresolvedExternalDependency) {
        String libraryName = mergedDependency.getId().getPresentableName();
        final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName, true);
        final String failureMessage = ((UnresolvedExternalDependency)mergedDependency).getFailureMessage();

        boolean isOfflineWork = resolverCtx.getSettings() != null && resolverCtx.getSettings().isOfflineWork();
        BuildIssue buildIssue = new UnresolvedDependencySyncIssue(
          libraryName, failureMessage, resolverCtx.getProjectPath(), isOfflineWork, ownerModule.getId());
        resolverCtx.report(MessageEvent.Kind.ERROR, buildIssue);

        LibraryLevel level = linkProjectLibrary(ideProject, library) ? LibraryLevel.PROJECT : LibraryLevel.MODULE;
        LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
        libraryDependencyData.setScope(dependencyScope);
        libraryDependencyData.setOrder(mergedDependency.getClasspathOrder() + classpathOrderShift);
        ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
      }

      if (depOwnerDataNode != null && !transitiveDependencies.isEmpty()) {
        doBuildDependencies(resolverCtx, sourceSetMap, artifactsMap, mergedDependencyMap, depOwnerDataNode, transitiveDependencies,
                            ideProject);
      }
    }
  }

  private static boolean isTestSourceSet(@NotNull ExternalSourceSet sourceSet) {
    if (sourceSet.getSources().isEmpty()) return false;
    return sourceSet.getSources().keySet().stream().allMatch(IExternalSystemSourceType::isTest);
  }

  private static Collection<File> collectProcessedArtifacts(Collection<ProjectDependencyInfo> infos) {
    return infos.stream().flatMap((info) -> info.dependencyArtifacts.stream()).collect(Collectors.toSet());
  }

  private static boolean doesNotContainDependencyOn(Collection<ProjectDependencyInfo> infos, GradleSourceSetData data) {
    return infos.stream().noneMatch((info) -> info.myModuleData.equals(data));
  }

  private static final Key<Map<String, DataNode<LibraryData>>> LIBRARIES_BY_NAME_CACHE =
    Key.create("GradleProjectResolverUtil.FOUND_LIBRARIES");

  /**
   * @deprecated use {@link GradleProjectResolverUtil#linkProjectLibrary(DataNode, LibraryData)} instead
   */
  @Deprecated
  public static boolean linkProjectLibrary(/*@NotNull*/ ProjectResolverContext context,
                                                        @Nullable DataNode<ProjectData> ideProject,
                                                        @NotNull final LibraryData library) {
    return linkProjectLibrary(ideProject, library);
  }

  public static boolean linkProjectLibrary(
    @Nullable DataNode<ProjectData> ideProject,
    @NotNull final LibraryData library) {
    if (ideProject == null) return false;

    Map<String, DataNode<LibraryData>> cache = ideProject.getUserData(LIBRARIES_BY_NAME_CACHE);
    if (cache == null) {
      cache = new HashMap<>();
      ideProject.putUserData(LIBRARIES_BY_NAME_CACHE, cache);
    }

    String libraryName = library.getExternalName();

    DataNode<LibraryData> libraryData = cache.computeIfAbsent(libraryName, (String name) -> {
      DataNode<LibraryData> newValueToCache =
        ExternalSystemApiUtil.find(ideProject, ProjectKeys.LIBRARY, node -> libraryName.equals(node.getData().getExternalName()));
      if (newValueToCache == null) {
        newValueToCache = ideProject.createChild(ProjectKeys.LIBRARY, library);
      }
      return newValueToCache;
    });

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

  @ApiStatus.Internal
  public static Stream<GradleProjectResolverExtension> createProjectResolvers(@Nullable ProjectResolverContext projectResolverContext) {
    return GradleProjectResolverExtension.EP_NAME.getExtensionList().stream().map(extension -> {
      try {
        Constructor<? extends GradleProjectResolverExtension> constructor = extension.getClass().getDeclaredConstructor();
        constructor.setAccessible(true);
        GradleProjectResolverExtension resolverExtension = constructor.newInstance();
        if (projectResolverContext != null) {
          resolverExtension.setProjectResolverContext(projectResolverContext);
        }
        return resolverExtension;
      }
      catch (Exception e) {
        LOG.error(e);
      }
      return null;
    }).sorted(ExternalSystemApiUtil.ORDER_AWARE_COMPARATOR);
  }

  static class ProjectDependencyInfo {
    @NotNull final ModuleData myModuleData;
    @Nullable final ExternalSourceSet mySourceSet;
    final Collection<File> dependencyArtifacts;

    ProjectDependencyInfo(@NotNull ModuleData moduleData,
                          @Nullable ExternalSourceSet sourceSet,
                          Collection<File> dependencyArtifacts) {
      this.myModuleData = moduleData;
      this.mySourceSet = sourceSet;
      this.dependencyArtifacts = dependencyArtifacts;
    }
  }
}