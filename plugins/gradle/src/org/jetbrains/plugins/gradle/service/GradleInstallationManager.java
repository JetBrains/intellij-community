// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.NioPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.target.GradleTargetUtil;
import org.jetbrains.plugins.gradle.service.execution.BuildLayoutParameters;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionAware;
import org.jetbrains.plugins.gradle.service.execution.LocalBuildLayoutParameters;
import org.jetbrains.plugins.gradle.service.execution.LocalGradleExecutionAware;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleEnvironment;
import org.jetbrains.plugins.gradle.util.GradleLog;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo;
import static org.jetbrains.plugins.gradle.util.GradleJvmResolutionUtil.getGradleJvmLookupProvider;
import static org.jetbrains.plugins.gradle.util.GradleJvmUtil.nonblockingResolveGradleJvmInfo;

/**
 * Provides discovery utilities about Gradle build environment/layout based on current system environment and IDE configuration.
 */
public class GradleInstallationManager implements Disposable {

  public static final Pattern GRADLE_JAR_FILE_PATTERN;
  public static final Pattern ANY_GRADLE_JAR_FILE_PATTERN;
  public static final Pattern ANT_JAR_PATTERN = Pattern.compile("ant(-(.*))?\\.jar");
  public static final Pattern IVY_JAR_PATTERN = Pattern.compile("ivy(-(.*))?\\.jar");

  private static final String[] GRADLE_START_FILE_NAMES;
  private static final @NonNls String GRADLE_ENV_PROPERTY_NAME;
  private static final Path BREW_GRADLE_LOCATION = Paths.get("/usr/local/Cellar/gradle/");
  private static final String LIBEXEC = "libexec";

  static {
    // Init static data with ability to redefine it locally.
    GRADLE_JAR_FILE_PATTERN = Pattern.compile(System.getProperty("gradle.pattern.core.jar", "gradle-(core-)?(\\d.*)\\.jar"));
    ANY_GRADLE_JAR_FILE_PATTERN = Pattern.compile(System.getProperty("gradle.pattern.core.jar", "gradle-(.*)\\.jar"));
    GRADLE_START_FILE_NAMES = System.getProperty("gradle.start.file.names", "gradle:gradle.cmd:gradle.sh").split(":");
    GRADLE_ENV_PROPERTY_NAME = System.getProperty("gradle.home.env.key", "GRADLE_HOME");
  }

  @Override
  public void dispose() {
  }

  public static GradleInstallationManager getInstance() {
    return ApplicationManager.getApplication().getService(GradleInstallationManager.class);
  }

  private @Nullable Ref<Path> myCachedGradleHomeFromPath;
  private final Map<String, BuildLayoutParameters> myBuildLayoutParametersCache = new ConcurrentHashMap<>();

  private static String getDefaultProjectKey(Project project) {
    return project.getLocationHash();
  }

  @ApiStatus.Experimental
  public static @NotNull BuildLayoutParameters defaultBuildLayoutParameters(@NotNull Project project) {
    return getInstance().guessBuildLayoutParameters(project, null);
  }

  /**
   * Tries to guess build layout parameters for the Gradle build located at {@code projectPath}.
   * Returns default parameters if {@code projectPath} is not passed in.
   */
  @ApiStatus.Experimental
  public @NotNull BuildLayoutParameters guessBuildLayoutParameters(@NotNull Project project, @Nullable String projectPath) {
    return myBuildLayoutParametersCache.computeIfAbsent(ObjectUtils.notNull(projectPath, getDefaultProjectKey(project)), p -> {
      for (ExternalSystemExecutionAware executionAware : ExternalSystemExecutionAware.getExtensions(GradleConstants.SYSTEM_ID)) {
        if (!(executionAware instanceof GradleExecutionAware gradleExecutionAware)) continue;
        BuildLayoutParameters buildLayoutParameters;
        if (projectPath == null) {
          buildLayoutParameters = gradleExecutionAware.getDefaultBuildLayoutParameters(project);
        }
        else {
          buildLayoutParameters = gradleExecutionAware.getBuildLayoutParameters(project, Path.of(projectPath));
        }
        if (buildLayoutParameters != null) {
          return buildLayoutParameters;
        }
      }
      if (projectPath != null) {
        return new LocalGradleExecutionAware().getBuildLayoutParameters(project, Path.of(projectPath));
      }
      else {
        return new LocalGradleExecutionAware().getDefaultBuildLayoutParameters(project);
      }
    });
  }

  public @Nullable Path getGradleHomePath(@Nullable Project project, @NotNull String linkedProjectPath) {
    if (project == null) {
      return null;
    }
    BuildLayoutParameters buildLayoutParameters = guessBuildLayoutParameters(project, linkedProjectPath);
    return GradleTargetUtil.maybeGetLocalValue(buildLayoutParameters.getGradleHome());
  }

  /**
   * @deprecated Use {@link #getGradleHomePath(Project, String)} instead
   */
  @Deprecated
  public @Nullable File getGradleHome(@Nullable Project project, @NotNull String linkedProjectPath) {
    Path mayBePath = getGradleHomePath(project, linkedProjectPath);
    if (mayBePath != null) {
      return mayBePath.toFile();
    }
    return null;
  }

  /**
   * Tries to deduce gradle location from current environment.
   *
   * @return gradle home deduced from the current environment (if any); {@code null} otherwise
   */
  public @Nullable Path getAutodetectedGradleHome(@Nullable Project project) {
    Path result = getGradleHomeFromPath(project);
    if (result != null) return result;

    result = getGradleHomeFromEnvProperty(project);
    if (result != null) return result;

    if (SystemInfo.isMac) {
      return getGradleHomeFromBrew();
    }
    return null;
  }

  /**
   * Tries to suggest a better path for the Gradle home
   *
   * @param homePath expected path to gradle home
   * @return proper in terms of {@link #isGradleSdkHome(Project, Path)} path or {@code null} if it is impossible to fix path
   */
  public @NlsSafe Path suggestBetterGradleHomePath(@Nullable Project project, @NotNull Path path) {
    if (path.startsWith(BREW_GRADLE_LOCATION)) {
      Path libexecPath = path.resolve(LIBEXEC);
      if (isGradleSdkHome(project, libexecPath)) {
        return libexecPath;
      }
    }
    return null;
  }

  public @Nullable String getGradleJvmPath(@NotNull Project project, @NotNull String linkedProjectPath) {
    final GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath);
    if (settings == null) {
      return getAvailableJavaHome(project);
    }
    String gradleJvm = settings.getGradleJvm();
    SdkLookupProvider sdkLookupProvider = getGradleJvmLookupProvider(project, settings);
    SdkInfo sdkInfo = nonblockingResolveGradleJvmInfo(sdkLookupProvider, project, linkedProjectPath, gradleJvm);
    if (sdkInfo instanceof SdkInfo.Resolved) {
      return ((SdkInfo.Resolved)sdkInfo).getHomePath();
    }
    return null;
  }

  /**
   * Tries to discover gradle installation path from the configured system path
   *
   * @return file handle for the gradle directory if it's possible to deduce from the system path; {@code null} otherwise
   */
  private @Nullable Path getGradleHomeFromPath(@Nullable Project project) {
    Ref<Path> ref = myCachedGradleHomeFromPath;
    if (ref != null) {
      return ref.get();
    }
    String path = System.getenv("PATH");
    if (path == null) {
      return null;
    }
    for (String pathEntry : path.split(File.pathSeparator)) {
      Path dir = Path.of(pathEntry);
      if (!Files.isDirectory(dir)) {
        continue;
      }
      for (String fileName : GRADLE_START_FILE_NAMES) {
        Path startFile = dir.resolve(fileName);
        if (Files.isRegularFile(startFile)) {
          Path candidate = dir.getParent();
          if (isGradleSdkHome(project, candidate)) {
            myCachedGradleHomeFromPath = new Ref<>(candidate);
            return candidate;
          }
        }
      }
    }
    return null;
  }

  /**
   * Tries to discover gradle installation via environment property.
   *
   * @return file handle for the gradle directory deduced from the environment where the project is located
   */
  private @Nullable Path getGradleHomeFromEnvProperty(@Nullable Project project) {
    String path = System.getenv(GRADLE_ENV_PROPERTY_NAME);
    if (path == null) {
      return null;
    }
    Path candidate = Path.of(path);
    return isGradleSdkHome(project, candidate) ? candidate : null;
  }

  /**
   * Allows to answer if given virtual file points to the gradle installation root.
   *
   * @param project current IDE project
   * @param file gradle installation root candidate
   * @return {@code true} if we consider that given file actually points to the gradle installation root;
   * {@code false} otherwise
   */
  public boolean isGradleSdkHome(@Nullable Project project, @Nullable Path file) {
    if (file == null) {
      return false;
    }
    if (project == null) {
      ProjectManager projectManager = ProjectManager.getInstance();
      Project[] openProjects = projectManager.getOpenProjects();
      project = openProjects.length > 0 ? openProjects[0] : projectManager.getDefaultProject();
    }
    for (ExternalSystemExecutionAware executionAware : ExternalSystemExecutionAware.getExtensions(GradleConstants.SYSTEM_ID)) {
      if (!(executionAware instanceof GradleExecutionAware gradleExecutionAware)) continue;
      if (gradleExecutionAware.isGradleInstallationHomeDir(project, file)) {
        return true;
      }
    }
    return false;
  }

  public @Nullable List<Path> getClassRoots(@Nullable Project project, @Nullable String rootProjectPath) {
    if (project == null) {
      return null;
    }
    if (rootProjectPath == null) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        rootProjectPath = ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath();
        List<Path> result = findGradleSdkClasspath(project, rootProjectPath);
        if (!result.isEmpty()) return result;
      }
    }
    else {
      return findGradleSdkClasspath(project, rootProjectPath);
    }
    return null;
  }

  /**
   * Allows to answer if given files contain the one from gradle installation.
   *
   * @param files files to process
   * @return {@code true} if one of the given files is from the gradle installation; {@code false} otherwise
   */
  public boolean isGradleSdk(VirtualFile... files) {
    if (files == null) {
      return false;
    }
    for (VirtualFile file : files) {
      if (findGradleJar(file.toNioPath()) != null) {
        return true;
      }
    }
    return false;
  }

  private List<Path> findGradleSdkClasspath(@NotNull Project project, @Nullable String rootProjectPath) {
    if (rootProjectPath == null) {
      return Collections.emptyList();
    }
    Path gradleHome = getGradleHomePath(project, rootProjectPath);
    if (gradleHome == null || !Files.isDirectory(gradleHome)) {
      return Collections.emptyList();
    }
    List<Path> result = new ArrayList<>();
    Path src = gradleHome.resolve("src");
    if (Files.isDirectory(src)) {
      if (Files.isDirectory(src.resolve("org"))) {
        addRoots(result, src);
      }
      else {
        listFolder(src, file -> addRoots(result, file));
      }
    }
    final Collection<Path> libraries = getAllLibraries(gradleHome);
    if (libraries == null) {
      return result;
    }
    for (Path file : libraries) {
      if (isGradleBuildClasspathLibrary(file)) {
        ContainerUtil.addIfNotNull(result, file);
      }
    }
    return result;
  }

  private static void listFolder(@Nullable Path folder, @NotNull Consumer<Path> pathConsumer) {
    if (folder == null || !Files.isDirectory(folder)) {
      return;
    }
    try (Stream<Path> files = Files.list(folder)) {
      files.forEach(pathConsumer);
    }
    catch (IOException e) {
      throw new IllegalStateException("Unable to list files in folder " + folder, e);
    }
  }

  /**
   * Allows to get file handles for the gradle binaries to use.
   *
   * @param gradleHome gradle sdk home
   * @return file handles for the gradle binaries; {@code null} if gradle is not discovered
   */
  private static @Nullable Collection<Path> getAllLibraries(@Nullable Path gradleHome) {
    if (gradleHome == null || !Files.isDirectory(gradleHome)) {
      return null;
    }
    List<Path> result = new ArrayList<>();
    listFolder(gradleHome.resolve("lib"), file -> {
      if (file.getFileName().toString().endsWith(".jar")) {
        result.add(file);
      }
    });
    listFolder(gradleHome.resolve("lib/plugins"), library -> {
      if (library.getFileName().toString().endsWith(".jar")) {
        result.add(library);
      }
    });
    return result.isEmpty() ? null : result;
  }

  private static @Nullable Path findGradleJar(@Nullable Path file) {
    if (file == null) {
      return null;
    }
    if (GRADLE_JAR_FILE_PATTERN.matcher(file.getFileName().toString()).matches()) {
      return file;
    }
    if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
      StringBuilder filesInfo = new StringBuilder();
      filesInfo.append(file).append(';');
      if (!filesInfo.isEmpty()) {
        filesInfo.setLength(filesInfo.length() - 1);
      }
      GradleLog.LOG.info(String.format(
        "Gradle sdk check fails. Reason: no one of the given files matches gradle JAR pattern (%s). Files: %s",
        GRADLE_JAR_FILE_PATTERN, filesInfo
      ));
    }
    return null;
  }

  public static @Nullable String getGradleVersion(@Nullable Path gradleHome) {
    if (gradleHome == null) {
      return null;
    }
    Path libs = gradleHome.resolve("lib");
    if (!Files.isDirectory(libs)) {
      return null;
    }
    try (Stream<Path> children = Files.list(libs)) {
      return children.map(path -> {
          Path fileName = path.getFileName();
          if (fileName != null) {
            Matcher matcher = GRADLE_JAR_FILE_PATTERN.matcher(fileName.toString());
            if (matcher.matches()) {
              return matcher.group(2);
            }
          }
          return null;
        })
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
    }
    catch (IOException e) {
      return null;
    }
  }

  /**
   * @deprecated Use {@link GradleInstallationManager#getGradleVersion(Path)} instead.
   */
  @Deprecated
  public static @Nullable String getGradleVersion(@Nullable String gradleHome) {
    if (gradleHome == null) {
      return null;
    }
    Path nioGradleHome = NioPathUtil.toNioPathOrNull(gradleHome);
    return getGradleVersion(nioGradleHome);
  }

  private static @Nullable Path getGradleHomeFromBrew() {
    try {
      try (DirectoryStream<Path> ds = Files.newDirectoryStream(BREW_GRADLE_LOCATION)) {
        Path bestPath = null;
        Version highestVersion = null;
        for (Path path : ds) {
          String fileName = path.getFileName().toString();
          try {
            Version version = Version.parseVersion(fileName);
            if (version == null) continue;
            if (highestVersion == null || version.compareTo(highestVersion) > 0) {
              highestVersion = version;
              bestPath = path;
            }
          }
          catch (NumberFormatException ignored) {
          }
        }
        if (bestPath != null) {
          Path libexecPath = bestPath.resolve(LIBEXEC);
          if (Files.exists(libexecPath)) {
            return libexecPath;
          }
        }
      }
    }
    catch (Exception ignored) {

    }
    return null;
  }

  private static boolean isGradleBuildClasspathLibrary(@NotNull Path file) {
    String fileName = file.getFileName().toString();
    return ANY_GRADLE_JAR_FILE_PATTERN.matcher(fileName).matches()
           || ANT_JAR_PATTERN.matcher(fileName).matches()
           || IVY_JAR_PATTERN.matcher(fileName).matches()
           || isGroovyJar(fileName);
  }

  private static void addRoots(@NotNull List<? super Path> result, Path... files) {
    if (files == null) return;
    for (Path file : files) {
      if (file == null || !Files.isDirectory(file)) {
        continue;
      }
      result.add(file);
    }
  }

  private static boolean isGroovyJar(@NotNull String name) {
    name = StringUtil.toLowerCase(name);
    return name.startsWith("groovy-") && name.endsWith(".jar") && !name.contains("src") && !name.contains("doc");
  }

  /**
   * Allows to execute gradle tasks in non imported gradle project
   *
   * @see <a href="https://youtrack.jetbrains.com/issue/IDEA-199979">IDEA-199979</a>
   */
  private static @Nullable String getAvailableJavaHome(@NotNull Project project) {
    Pair<String, Sdk> sdkPair = ExternalSystemJdkUtil.getAvailableJdk(project);
    if (ExternalSystemJdkUtil.isValidJdk(sdkPair.second)) {
      return sdkPair.second.getHomePath();
    }
    return null;
  }

  public static @Nullable GradleVersion guessGradleVersion(@NotNull GradleProjectSettings settings) {
    DistributionType distributionType = settings.getDistributionType();
    if (distributionType == null) return null;
    BuildLayoutParameters buildLayoutParameters;
    Project project = findProject(settings);
    if (project == null)  {
      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      buildLayoutParameters =
        new LocalBuildLayoutParameters(defaultProject, NioPathUtil.toNioPathOrNull(settings.getExternalProjectPath())) {
        @Override
        public GradleProjectSettings getGradleProjectSettings() {
          return settings;
        }
      };
    } else {
      buildLayoutParameters = getInstance().guessBuildLayoutParameters(project, settings.getExternalProjectPath());
    }
    return buildLayoutParameters.getGradleVersion();
  }

  public static @Nullable GradleVersion parseDistributionVersion(@NotNull String path) {
    path = StringUtil.substringAfterLast(path, "/");
    if (path == null) return null;

    path = StringUtil.substringAfterLast(path, "gradle-");
    if (path == null) return null;

    int i = path.lastIndexOf('-');
    if (i <= 0) return null;

    return getGradleVersionSafe(path.substring(0, i));
  }

  public static @Nullable GradleVersion getGradleVersionSafe(@NotNull String gradleVersion) {
    try {
      return GradleVersion.version(gradleVersion);
    }
    catch (IllegalArgumentException e) {
      // GradleVersion.version(gradleVersion) might throw exception for custom Gradle versions
      // https://youtrack.jetbrains.com/issue/IDEA-216892
      return null;
    }
  }

  private static @Nullable Project findProject(@NotNull GradleProjectSettings settings) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      GradleProjectSettings linkedProjectSettings =
        GradleSettings.getInstance(project).getLinkedProjectSettings(settings.getExternalProjectPath());
      if (linkedProjectSettings == settings) {
        return project;
      }
    }
    return null;
  }

  @ApiStatus.Internal
  static final class ProjectManagerLayoutParametersCacheCleanupListener implements ProjectManagerListener {
    @Override
    public void projectClosed(@NotNull Project project) {
      getInstance().myBuildLayoutParametersCache.clear();
    }
  }

  @ApiStatus.Internal
  static final class DynamicPluginLayoutParametersCacheCleanupListener implements DynamicPluginListener {
    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
      getInstance().myBuildLayoutParametersCache.clear();
    }
  }

  @ApiStatus.Internal
  static final class TaskNotificationLayoutParametersCacheCleanupListener implements ExternalSystemTaskNotificationListener {

    @Override
    public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
      getInstance().myBuildLayoutParametersCache.remove(projectPath);
    }

    @Override
    public void onEnd(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
      // it is not enough to clean up cache on the start of an external event, because sometimes the changes occur `after` the event finishes.
      // An example of this behavior is the downloading of gradle distribution:
      // we must not rely on the caches that were computed without downloaded distribution.
      if (!(id.getProjectSystemId() == GradleConstants.SYSTEM_ID && id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT)) {
        return;
      }
      Project project = id.findProject();
      if (project == null) {
        return;
      }
      GradleInstallationManager installationManager = getInstance();
      installationManager.myBuildLayoutParametersCache.remove(getDefaultProjectKey(project));
      GradleSettings settings = GradleSettings.getInstance(project);
      for (GradleProjectSettings linkedSettings : settings.getLinkedProjectsSettings()) {
        String path = linkedSettings.getExternalProjectPath();
        installationManager.myBuildLayoutParametersCache.remove(path);
      }
    }
  }
}
