// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.buildsystem.model.unified.UnifiedCoordinates;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
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
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.*;
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId;
import org.jetbrains.plugins.gradle.ExternalDependencyId;
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencySyncIssue;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.cache.GradleLocalCacheHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleDataKt;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.util.NullUtils.hasNull;
import static org.jetbrains.plugins.gradle.service.cache.GradleLocalCacheHelper.findArtifactComponents;

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
    if (GradleProjectResolver.DEBUG_ORPHAN_MODULES_PROCESSING) {
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
    boolean isComposite = !resolverCtx.getNestedBuilds().isEmpty();
    boolean isIncludedBuildTaskRunningSupported = isComposite && isIncludedBuildTaskRunningSupported(projectDataNode, resolverCtx);
    File mainBuildRootDir = resolverCtx.getRootBuild().getBuildIdentifier().getRootDir();
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
      isSupported = gradleVersion != null && GradleVersionUtil.isGradleAtLeast(gradleVersion, "6.8");
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
    String rootName = gradleModule.getProject().getName();
    BuildIdentifier buildIdentifier = gradleModule.getGradleProject().getProjectIdentifier().getBuildIdentifier();
    String buildSrcGroup = resolverCtx.getBuildSrcGroup(rootName, buildIdentifier);
    if (resolverCtx.isUseQualifiedModuleNames()) {
      delimiter = ".";
      if (StringUtil.isNotEmpty(buildSrcGroup)) {
        moduleName.append(buildSrcGroup).append(delimiter);
      }
      moduleName.append(gradlePathToQualifiedName(rootName, externalProject.getQName()));
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

  public static @NotNull String getModuleId(
    @NotNull ProjectResolverContext context,
    @NotNull IdeaModule gradleModule
  ) {
    ExternalProject externalProject = getExternalProject(context, gradleModule);
    return getModuleId(context, externalProject);
  }

  public static @NotNull String getModuleId(
    @NotNull ProjectResolverContext context,
    @NotNull IdeaModule gradleModule,
    @NotNull ExternalSourceSet sourceSet
  ) {
    ExternalProject externalProject = getExternalProject(context, gradleModule);
    return getModuleId(context, externalProject, sourceSet);
  }

  private static @NotNull ExternalProject getExternalProject(
    @NotNull ProjectResolverContext context,
    @NotNull IdeaModule gradleModule
  ) {
    ExternalProject externalProject = context.getProjectModel(gradleModule, ExternalProject.class);
    if (externalProject == null) {
      throw new IllegalStateException(
        "Missing " + ExternalProject.class.getSimpleName() + " for " + gradleModule.getProjectIdentifier().getProjectPath()
      );
    }
    return externalProject;
  }

  public static @NotNull String getModuleId(
    @NotNull ProjectResolverContext context,
    @NotNull ExternalProject externalProject,
    @NotNull ExternalSourceSet sourceSet
  ) {
    String mainModuleId = getModuleId(context, externalProject);
    return mainModuleId + ":" + sourceSet.getName();
  }

  public static @NotNull String getModuleId(
    @NotNull ProjectResolverContext context,
    @NotNull ExternalProject externalProject
  ) {
    if (!StringUtil.isEmpty(context.getBuildSrcGroup())) {
      return context.getBuildSrcGroup() + ":" + getModuleId(externalProject);
    }
    return getModuleId(externalProject);
  }

  private static @NotNull String getModuleId(
    @NotNull ExternalProject externalProject
  ) {
    String moduleName = externalProject.getName();
    String gradlePath = externalProject.getIdentityPath();
    if (StringUtil.isEmpty(gradlePath)) {
      return moduleName;
    }
    if (":".equals(gradlePath)) {
      return moduleName;
    }
    return gradlePath;
  }

  @TestOnly
  public static @NotNull String getModuleId(
    @NotNull ExternalProject externalProject,
    @NotNull ExternalSourceSet sourceSet
  ) {
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

  @NotNull
  private static DependencyScope getMergedDependencyScope(@Nullable String scope1, @Nullable String scope2) {
    return DependencyScope.coveringUseCasesOf(getDependencyScope(scope1), getDependencyScope(scope2));
  }

  public static void attachGradleSdkSources(@NotNull final IdeaModule gradleModule,
                                            @Nullable final File libFile,
                                            @NotNull final LibraryData library,
                                            @NotNull final ProjectResolverContext resolverCtx) {
    if (libFile == null || !libFile.getName().startsWith("gradle-")) return;
    final GradleBuildScriptClasspathModel buildScriptClasspathModel =
      resolverCtx.getExtraProject(gradleModule, GradleBuildScriptClasspathModel.class);
    if (buildScriptClasspathModel == null) return;
    final File gradleHomeDir = buildScriptClasspathModel.getGradleHomeDir();
    if (gradleHomeDir == null) return;
    final String gradleVersion = buildScriptClasspathModel.getGradleVersion();
    attachGradleSdkSources(libFile, library, gradleHomeDir, gradleVersion);
  }

  public static void attachGradleSdkSources(@Nullable final File libFile,
                                            @NotNull final LibraryData library,
                                            @NotNull final File gradleHomeDir,
                                            @NotNull final String gradleVersion) {
    if (libFile == null || !libFile.getName().startsWith("gradle-")) return;
    if (!FileUtil.isAncestor(gradleHomeDir, libFile, true)) {
      File libFileParent = libFile.getParentFile();
      if (libFileParent == null || !StringUtil.equals("generated-gradle-jars", libFileParent.getName())) return;
      if (("gradle-api-" + gradleVersion + ".jar").equals(libFile.getName())) {
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

      int endIndex = libFile.getName().indexOf(gradleVersion);
      if (endIndex != -1) {
        String srcDirChild = libFile.getName().substring("gradle-".length(), endIndex - 1);
        srcDir = new File(srcDir, srcDirChild);
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
        if (path.contains("/caches/modules-2/files-2.1") || path.contains("/caches/transforms-")) {
          UnifiedCoordinates coordinates = getLibraryCoordinates(libraryData);
          Set<LibraryPathType> requiredComponentTypes = getRequiredComponentTypes(sourceResolved, docResolved);
          final Map<LibraryPathType, List<Path>> cachedComponents;
          if (coordinates != null) {
            cachedComponents = findArtifactComponents(coordinates, gradleUserHomeDir.toPath(), requiredComponentTypes);
          }
          else {
            cachedComponents = findAdjacentComponents(Paths.get(path), requiredComponentTypes);
          }
          mergeCollectedArtifacts(collectedPaths, cachedComponents);
        }
        else {
          collectSourcesAndJavadocsFromTheSameFolder(path, collectedPaths, sourceResolved, docResolved);
        }
      }

      for (Map.Entry<LibraryPathType, List<String>> each : collectedPaths.entrySet()) {
        for (String cachedPath : each.getValue()) {
          libraryData.addPath(each.getKey(), cachedPath);
        }
      }
    }
  }

  private static void mergeCollectedArtifacts(@NotNull Map<LibraryPathType, List<String>> target,
                                              @NotNull Map<LibraryPathType, List<Path>> collected) {
    for (Map.Entry<LibraryPathType, List<Path>> entry : collected.entrySet()) {
      Set<String> pathStrings = entry.getValue()
        .stream()
        .map(v -> v.toString())
        .collect(Collectors.toSet());
      target.computeIfAbsent(entry.getKey(), ignore -> new SmartList<>()).addAll(pathStrings);
    }
  }

  private static @NotNull Set<LibraryPathType> getRequiredComponentTypes(boolean sourceResolved, boolean docResolved) {
    Set<LibraryPathType> requiredComponentTypes = EnumSet.noneOf(LibraryPathType.class);
    if (!sourceResolved) {
      requiredComponentTypes.add(LibraryPathType.SOURCE);
    }
    if (!docResolved) {
      requiredComponentTypes.add(LibraryPathType.DOC);
    }
    return requiredComponentTypes;
  }

  private static void collectSourcesAndJavadocsFromTheSameFolder(@NotNull String binaryPath,
                                                                 @NotNull Map<LibraryPathType, List<String>> collect,
                                                                 boolean sourceResolved,
                                                                 boolean docResolved) {
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
    catch (IOException e) {
      LOG.debug(e);
    }
  }

  private static @Nullable UnifiedCoordinates getLibraryCoordinates(@NotNull LibraryData libraryData) {
    String group = libraryData.getGroupId();
    String artifact = libraryData.getArtifactId();
    String version = libraryData.getVersion();
    if (hasNull(group, artifact, version)) {
      String externalName = libraryData.getExternalName();
      if (StringUtil.isEmpty(externalName)) {
        return null;
      }
      String[] externalNameParticles = externalName.split(":");
      if (externalNameParticles.length == 3) {
        group = externalNameParticles[0];
        artifact = externalNameParticles[1];
        version = externalNameParticles[2];
      }
    }
    if (hasNull(group, artifact, version)) {
      return null;
    }
    version = version.replace("@aar", "");
    return new UnifiedCoordinates(group, artifact, version);
  }

  public static void buildDependencies(@NotNull ProjectResolverContext resolverCtx,
                                       @NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
                                       @NotNull final ArtifactMappingService artifactsMap,
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
        if (dependency instanceof ExternalLibraryDependency libDependency) {
          if (seenDependency instanceof ExternalLibraryDependency seenLibDependency &&
              !FileUtil.filesEqual(seenLibDependency.getFile(), libDependency.getFile())) {
            DefaultExternalMultiLibraryDependency mergedDependency = new DefaultExternalMultiLibraryDependency(seenLibDependency);
            mergedDependency.setScope(getMergedDependencyScope(mergedDependency.getScope(), libDependency.getScope()).name());
            mergedDependency.addArtifactsFrom(libDependency);
            dependencyMap.put(key, mergedDependency);
            continue;
          }
          else if (seenDependency instanceof DefaultExternalMultiLibraryDependency mergedDependency) {
            mergedDependency.setScope(getMergedDependencyScope(mergedDependency.getScope(), libDependency.getScope()).name());
            mergedDependency.addArtifactsFrom(libDependency);
            continue;
          }
        }

        DependencyScope prevScope = getDependencyScope(seenDependency.getScope());
        DependencyScope currentScope = getDependencyScope(dependency.getScope());

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
                                          @NotNull final ArtifactMappingService artifactsMap,
                                          @NotNull Map<ExternalDependencyId, ExternalDependency> mergedDependencyMap,
                                          @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                          @NotNull Collection<ExternalDependency> dependencies,
                                          @Nullable DataNode<ProjectData> ideProject) throws IllegalStateException {
    AtomicInteger classpathOrderShift = new AtomicInteger(0);
    Set<ExternalDependencyId> processedDependencies = new HashSet<>();
    Map<ExternalDependencyId, Collection<ExternalDependency>> transitiveDependenciesMap = groupTransitiveDependenciesById(dependencies);

    for (ExternalDependency dependency : dependencies) {
      if (processedDependencies.add(dependency.getId())) {
        Collection<ExternalDependency> transitiveDependencies = transitiveDependenciesMap.getOrDefault(dependency.getId(), Collections.emptySet());
        final ExternalDependency mergedDependency = mergedDependencyMap.getOrDefault(dependency.getId(), dependency);
        DataNode<? extends ExternalEntityData> depOwnerDataNode = null;

        if (mergedDependency instanceof ExternalProjectDependency projectDependency) {
          depOwnerDataNode = createDependencyDataNode(projectDependency,
                                                      resolverCtx,
                                                      ownerDataNode,
                                                      transitiveDependencies.isEmpty(),
                                                      sourceSetMap,
                                                      artifactsMap,
                                                      classpathOrderShift);
        }
        else if (mergedDependency instanceof ExternalLibraryDependency libraryDependency) {
          depOwnerDataNode = createDependencyDataNode(libraryDependency, ownerDataNode, ideProject, classpathOrderShift);
        }
        else if (mergedDependency instanceof ExternalMultiLibraryDependency multiDep) {
          depOwnerDataNode = createDependencyDataNode(multiDep, ownerDataNode, classpathOrderShift);
        }
        else if (mergedDependency instanceof FileCollectionDependency fileCollectionDependency) {
          depOwnerDataNode = createDependencyDataNode(fileCollectionDependency, ownerDataNode, classpathOrderShift);
        }
        else if (mergedDependency instanceof UnresolvedExternalDependency unresolvedDep) {
          depOwnerDataNode = createDependencyDataNode(unresolvedDep, resolverCtx, ownerDataNode, ideProject, classpathOrderShift);
        }

        if (depOwnerDataNode != null && !transitiveDependencies.isEmpty()) {
          doBuildDependencies(resolverCtx, sourceSetMap, artifactsMap, mergedDependencyMap, depOwnerDataNode, transitiveDependencies,
                              ideProject);
        }
      }
    }
  }

  private static DataNode<? extends ExternalEntityData> createDependencyDataNode(ExternalProjectDependency projectDependency,
                                                                                 @NotNull ProjectResolverContext resolverCtx,
                                                                                 @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                                                                 boolean isTransitiveDepsEmpty,
                                                                                 @NotNull Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap,
                                                                                 @NotNull ArtifactMappingService artifactMap,
                                                                                 AtomicInteger classpathOrderShift) {
    DataNode<? extends ExternalEntityData> resultDataNode = null;
    final ModuleData ownerModule = getOwnerModule(ownerDataNode);
    DependencyScope dependencyScope = getDependencyScope(projectDependency.getScope());

    Collection<ProjectDependencyInfo> projectDependencyInfos = new ArrayList<>();
    List<File> artifactsToKeepAsLibraries = new ArrayList<>();
    if (resolverCtx.getSettings() != null) {
      GradleExecutionWorkspace executionWorkspace = resolverCtx.getSettings().getExecutionWorkspace();
      ModuleData moduleData = executionWorkspace.findModuleDataByArtifacts(projectDependency.getProjectDependencyArtifacts());
      if (moduleData != null) {
        projectDependencyInfos.add(new ProjectDependencyInfo(moduleData, null, projectDependency.getProjectDependencyArtifacts()));
      }
    }
    if (projectDependencyInfos.isEmpty()) {

      Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> projectPair;
      MultiMap<Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>, File> projectPairs =
        new MultiMap<>(new Reference2ObjectLinkedOpenHashMap<>());

      for (File file : projectDependency.getProjectDependencyArtifacts()) {
        ModuleMappingInfo mapping = artifactMap.getModuleMapping(ExternalSystemApiUtil.toCanonicalPath(file.getPath()));
        if (mapping != null) {
          for (String moduleId : mapping.getModuleIds()) {
            projectPair = sourceSetMap.get(moduleId);

            if (projectPair == null) continue;
            projectPairs.putValue(projectPair, file);
          }
          if (mapping.getHasNonModulesContent()) {
            artifactsToKeepAsLibraries.add(file);
          }
        }
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
      LibraryDependencyData libraryDependencyData =
        createLibraryDependencyData(projectDependency, classpathOrderShift.get(), ownerModule, dependencyScope);

      if (!projectDependency.getProjectDependencyArtifacts().isEmpty()) {
        for (File artifact : projectDependency.getProjectDependencyArtifacts()) {
          libraryDependencyData.getTarget().addPath(LibraryPathType.BINARY, artifact.getPath());
        }
        resultDataNode = ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
      }
      else {
        resultDataNode = ownerDataNode;
      }
    }
    else {
      int i = 0;
      for (ProjectDependencyInfo projectDependencyInfo : projectDependencyInfos) {
        if (i++ > 0 && isTransitiveDepsEmpty) {
          classpathOrderShift.incrementAndGet();
        }
        ModuleDependencyData moduleDependencyData = new ModuleDependencyData(ownerModule, projectDependencyInfo.myModuleData);
        moduleDependencyData.setScope(dependencyScope);
        if (projectDependencyInfo.mySourceSet != null && isTestSourceSet(projectDependencyInfo.mySourceSet)) {
          moduleDependencyData.setProductionOnTestDependency(true);
        }
        moduleDependencyData.setOrder(projectDependency.getClasspathOrder() + classpathOrderShift.get());
        moduleDependencyData.setExported(projectDependency.getExported());
        moduleDependencyData.setModuleDependencyArtifacts(ContainerUtil.map(projectDependencyInfo.dependencyArtifacts, File::getPath));
        resultDataNode = ownerDataNode.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData);
      }

      for (File artifact : artifactsToKeepAsLibraries) {
        LibraryDependencyData data =
          createLibraryDependencyData(projectDependency, classpathOrderShift.incrementAndGet(), ownerModule, dependencyScope);
        data.getTarget().addPath(LibraryPathType.BINARY, artifact.getPath());
        ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, data);
      }

      // put transitive dependencies to the ownerDataNode,
      // since we can not determine from what project dependency artifact it was originated
      if (projectDependencyInfos.size() > 1) {
        resultDataNode = ownerDataNode;
      }
    }

    return resultDataNode;
  }

  @NotNull
  private static LibraryDependencyData createLibraryDependencyData(ExternalProjectDependency projectDependency,
                                               int classpathOrderShift,
                                               ModuleData ownerModule,
                                               DependencyScope dependencyScope) {
    final LibraryLevel level = LibraryLevel.MODULE;
    final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, "");
    LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
    libraryDependencyData.setScope(dependencyScope);
    libraryDependencyData.setOrder(projectDependency.getClasspathOrder() + classpathOrderShift);
    libraryDependencyData.setExported(projectDependency.getExported());
    return libraryDependencyData;
  }


  private static DataNode<? extends ExternalEntityData> createDependencyDataNode(ExternalLibraryDependency libraryDependency,
                                                                                 @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                                                                 @Nullable DataNode<ProjectData> ideProject,
                                                                                 AtomicInteger classpathOrderShift) {
    DependencyScope dependencyScope = getDependencyScope(libraryDependency.getScope());
    final ModuleData ownerModule = getOwnerModule(ownerDataNode);
    String libraryName = libraryDependency.getId().getPresentableName();
    LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName);
    library.setArtifactId(libraryDependency.getId().getName());
    library.setGroup(libraryDependency.getId().getGroup());
    library.setVersion(libraryDependency.getId().getVersion());

    library.addPath(LibraryPathType.BINARY, libraryDependency.getFile().getPath());
    File sourcePath = libraryDependency.getSource();
    if (sourcePath != null) {
      library.addPath(LibraryPathType.SOURCE, sourcePath.getPath());
    }
    File javaDocPath = libraryDependency.getJavadoc();
    if (javaDocPath != null) {
      library.addPath(LibraryPathType.DOC, javaDocPath.getPath());
    }

    LibraryLevel level = StringUtil.isNotEmpty(libraryName) ? LibraryLevel.PROJECT : LibraryLevel.MODULE;
    if (StringUtil.isEmpty(libraryName) || !linkProjectLibrary(ideProject, library)) {
      level = LibraryLevel.MODULE;
    }

    LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
    libraryDependencyData.setScope(dependencyScope);
    libraryDependencyData.setOrder(libraryDependency.getClasspathOrder() + classpathOrderShift.get());
    libraryDependencyData.setExported(libraryDependency.getExported());
    return ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
  }

  private static DataNode<? extends ExternalEntityData> createDependencyDataNode(ExternalMultiLibraryDependency multiDep,
                                                                                 @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                                                                 AtomicInteger classpathOrderShift) {
    final ModuleData ownerModule = getOwnerModule(ownerDataNode);
    DependencyScope dependencyScope = getDependencyScope(multiDep.getScope());

    final LibraryLevel level = LibraryLevel.MODULE;
    String libraryName = multiDep.getId().getPresentableName();
    final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName);
    library.setArtifactId(multiDep.getId().getName());
    library.setGroup(multiDep.getId().getGroup());
    library.setVersion(multiDep.getId().getVersion());
    LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
    libraryDependencyData.setScope(dependencyScope);
    libraryDependencyData.setOrder(multiDep.getClasspathOrder() + classpathOrderShift.get());
    libraryDependencyData.setExported(multiDep.getExported());

    for (File file : multiDep.getFiles()) {
      library.addPath(LibraryPathType.BINARY, file.getPath());
    }
    for (File file : multiDep.getSources()) {
      library.addPath(LibraryPathType.SOURCE, file.getPath());
    }
    for (File file : multiDep.getJavadoc()) {
      library.addPath(LibraryPathType.DOC, file.getPath());
    }

    return ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
  }



  @Nullable
  private static DataNode<? extends ExternalEntityData> createDependencyDataNode(FileCollectionDependency fileCollectionDependency,
                                                                                 @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                                                                 AtomicInteger classpathOrderShift) {
    final ModuleData ownerModule = getOwnerModule(ownerDataNode);
    DependencyScope dependencyScope = getDependencyScope(fileCollectionDependency.getScope());

    final LibraryLevel level = LibraryLevel.MODULE;
    String libraryName = "";
    final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName);
    LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
    libraryDependencyData.setScope(dependencyScope);
    libraryDependencyData.setOrder(fileCollectionDependency.getClasspathOrder() + classpathOrderShift.get());
    libraryDependencyData.setExported(fileCollectionDependency.getExported());

    for (File file : fileCollectionDependency.getFiles()) {
      library.addPath(LibraryPathType.BINARY, file.getPath());
      if (fileCollectionDependency instanceof DefaultFileCollectionDependency defaultFCDep &&
          defaultFCDep.isExcludedFromIndexing()) {
        library.addPath(LibraryPathType.EXCLUDED, file.getPath());
      }
    }

    ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
    return null;
  }


  @Nullable
  private static DataNode<? extends ExternalEntityData> createDependencyDataNode(UnresolvedExternalDependency unresolvedDep,
                                                                                 @NotNull ProjectResolverContext resolverCtx,
                                                                                 @NotNull DataNode<? extends ExternalEntityData> ownerDataNode,
                                                                                 @Nullable DataNode<ProjectData> ideProject,
                                                                                 AtomicInteger classpathOrderShift) {

    final ModuleData ownerModule = getOwnerModule(ownerDataNode);

    reportUnresolvedDependencyError(resolverCtx, unresolvedDep, ownerModule.getId());

    String libraryName = unresolvedDep.getId().getPresentableName();
    DependencyScope dependencyScope = getDependencyScope(unresolvedDep.getScope());
    final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName, true);
    LibraryLevel level = linkProjectLibrary(ideProject, library) ? LibraryLevel.PROJECT : LibraryLevel.MODULE;
    LibraryDependencyData libraryDependencyData = new LibraryDependencyData(ownerModule, library, level);
    libraryDependencyData.setScope(dependencyScope);
    libraryDependencyData.setOrder(unresolvedDep.getClasspathOrder() + classpathOrderShift.get());
    ownerDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData);
    return null;
  }

  private static void reportUnresolvedDependencyError(@NotNull ProjectResolverContext resolverCtx,
                                                      UnresolvedExternalDependency unresolvedDep,
                                                      String ownerModuleId) {
    final String libraryName =  unresolvedDep.getId().getPresentableName();
    final String failureMessage = unresolvedDep.getFailureMessage();
    boolean isOfflineWork = resolverCtx.getSettings() != null && resolverCtx.getSettings().isOfflineWork();
    BuildIssue buildIssue = new UnresolvedDependencySyncIssue(
      libraryName, failureMessage, resolverCtx.getProjectPath(), isOfflineWork, ownerModuleId);
    resolverCtx.report(MessageEvent.Kind.ERROR, buildIssue);
  }

  @NotNull
  private static ModuleData getOwnerModule(@NotNull DataNode<? extends ExternalEntityData> ownerDataNode) {
    ModuleData result = null;
    ExternalEntityData ownerData = ownerDataNode.getData();
    if (ownerData instanceof ModuleData moduleData) {
      result = moduleData;
    }
    else if (ownerData instanceof DependencyData<?> dependencyData) {
      result = dependencyData.getOwnerModule();
    }
    assert result != null;
    return result;
  }

  @NotNull
  private static Map<ExternalDependencyId, Collection<ExternalDependency>> groupTransitiveDependenciesById(@NotNull Collection<ExternalDependency> dependencies) {
    Map<ExternalDependencyId, Collection<ExternalDependency>> dependencyMap = new LinkedHashMap<>();
    for (ExternalDependency dependency : dependencies) {
      Collection<ExternalDependency> transitiveDependencies = dependencyMap.computeIfAbsent(dependency.getId(), __ -> new LinkedHashSet<>());
      transitiveDependencies.addAll(dependency.getDependencies());
    }
    return dependencyMap;
  }

  private static @NotNull Map<LibraryPathType, List<Path>> findAdjacentComponents(@NotNull Path binaryPath,
                                                                                  @NotNull Set<LibraryPathType> requestedComponents) {
    var parent = binaryPath.getParent();
    var cachedArtifactRoot = parent.getParent();
    return GradleLocalCacheHelper.findAdjacentComponents(cachedArtifactRoot, requestedComponents);
  }

  public static boolean isTestSourceSet(@NotNull ExternalSourceSet sourceSet) {
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