// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  @NonNls private static final String GRADLE_ENV_PROPERTY_NAME;
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

  @Nullable private Ref<File> myCachedGradleHomeFromPath;
  private final Map<String, BuildLayoutParameters> myBuildLayoutParametersCache = new ConcurrentHashMap<>();

  public GradleInstallationManager() {
    ExternalSystemTaskNotificationListenerAdapter listener = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
        myBuildLayoutParametersCache.remove(workingDir);
      }
    };
    ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(listener, this);
    AtomicBoolean listenerAdded = new AtomicBoolean(true);
    MessageBusConnection appConnection = ApplicationManager.getApplication().getMessageBus().connect();
    appConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        if (listenerAdded.compareAndSet(false, true)) {
          ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(listener, GradleInstallationManager.this);
        }
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        myBuildLayoutParametersCache.clear();
        if (listenerAdded.compareAndSet(true, false)) {
          ExternalSystemProgressNotificationManager.getInstance().removeNotificationListener(listener);
        }
      }
    });
    appConnection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        myBuildLayoutParametersCache.clear();
      }
    });
  }

  @ApiStatus.Experimental
  @NotNull
  public static BuildLayoutParameters defaultBuildLayoutParameters(@NotNull Project project) {
    return getInstance().guessBuildLayoutParameters(project, null);
  }

  /**
   * Tries to guess build layout parameters for the Gradle build located at {@code projectPath}.
   * Returns default parameters if {@code projectPath} is not passed in.
   */
  @ApiStatus.Experimental
  @NotNull
  public BuildLayoutParameters guessBuildLayoutParameters(@NotNull Project project, @Nullable String projectPath) {
    return myBuildLayoutParametersCache.computeIfAbsent(ObjectUtils.notNull(projectPath, project.getLocationHash()), p -> {
      for (ExternalSystemExecutionAware executionAware : ExternalSystemExecutionAware.getExtensions(GradleConstants.SYSTEM_ID)) {
        if (!(executionAware instanceof GradleExecutionAware)) continue;
        GradleExecutionAware gradleExecutionAware = (GradleExecutionAware)executionAware;
        BuildLayoutParameters buildLayoutParameters = projectPath == null
                                                      ? gradleExecutionAware.getDefaultBuildLayoutParameters(project)
                                                      : gradleExecutionAware.getBuildLayoutParameters(project, projectPath);
        if (buildLayoutParameters != null) {
          return buildLayoutParameters;
        }
      }
      return new LocalGradleExecutionAware().getBuildLayoutParameters(project, projectPath);
    });
  }

  /**
   * Allows to get file handles for the gradle binaries to use.
   *
   * @param gradleHome gradle sdk home
   * @return file handles for the gradle binaries; {@code null} if gradle is not discovered
   */
  @Nullable
  public Collection<File> getAllLibraries(@Nullable File gradleHome) {

    if (gradleHome == null || !gradleHome.isDirectory()) {
      return null;
    }

    List<File> result = new ArrayList<>();

    File libs = new File(gradleHome, "lib");
    File[] files = libs.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.getName().endsWith(".jar")) {
          result.add(file);
        }
      }
    }

    File plugins = new File(libs, "plugins");
    files = plugins.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.getName().endsWith(".jar")) {
          result.add(file);
        }
      }
    }
    return result.isEmpty() ? null : result;
  }

  @Nullable
  public File getGradleHome(@Nullable Project project, @NotNull String linkedProjectPath) {
    if (project == null) return null;
    BuildLayoutParameters buildLayoutParameters = guessBuildLayoutParameters(project, linkedProjectPath);
    String gradleHome = GradleTargetUtil.maybeGetLocalValue(buildLayoutParameters.getGradleHome());
    return gradleHome != null ? new File(gradleHome) : null;
  }

  public @Nullable String getGradleJvmPath(@NotNull Project project, @NotNull String linkedProjectPath) {
    final GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath);
    if (settings == null) return getAvailableJavaHome(project);

    String gradleJvm = settings.getGradleJvm();
    SdkLookupProvider sdkLookupProvider = getGradleJvmLookupProvider(project, settings);
    SdkInfo sdkInfo = nonblockingResolveGradleJvmInfo(sdkLookupProvider, project, linkedProjectPath, gradleJvm);
    if (sdkInfo instanceof SdkInfo.Resolved) return ((SdkInfo.Resolved)sdkInfo).getHomePath();
    return null;
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

  /**
   * Tries to deduce gradle location from current environment.
   *
   * @return gradle home deduced from the current environment (if any); {@code null} otherwise
   */
  @Nullable
  public File getAutodetectedGradleHome(@Nullable Project project) {
    File result = getGradleHomeFromPath(project);
    if (result != null) return result;

    result = getGradleHomeFromEnvProperty(project);
    if (result != null) return result;

    if (SystemInfo.isMac) {
      return getGradleHomeFromBrew();
    }
    return null;
  }

  @Nullable
  private static File getGradleHomeFromBrew() {
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
            return libexecPath.toFile();
          }
        }
      }
    }
    catch (Exception ignored) {
    }
    return null;
  }

  public String suggestBetterGradleHomePath(@NotNull String homePath) {
    return suggestBetterGradleHomePath(null, homePath);
  }

  /**
   * Tries to suggest better path to gradle home
   *
   * @param homePath expected path to gradle home
   * @return proper in terms of {@link #isGradleSdkHome(Project, File)} path or {@code null} if it is impossible to fix path
   */
  @NlsSafe
  public String suggestBetterGradleHomePath(@Nullable Project project, @NotNull String homePath) {
    Path path = Paths.get(homePath);
    if (path.startsWith(BREW_GRADLE_LOCATION)) {
      Path libexecPath = path.resolve(LIBEXEC);
      File libexecFile = libexecPath.toFile();
      if (isGradleSdkHome(project, libexecFile)) {
        return libexecPath.toString();
      }
    }
    return null;
  }

  /**
   * Tries to discover gradle installation path from the configured system path
   *
   * @return file handle for the gradle directory if it's possible to deduce from the system path; {@code null} otherwise
   * @param project
   */
  @Nullable
  public File getGradleHomeFromPath(@Nullable Project project) {
    Ref<File> ref = myCachedGradleHomeFromPath;
    if (ref != null) {
      return ref.get();
    }
    String path = System.getenv("PATH");
    if (path == null) {
      return null;
    }
    for (String pathEntry : path.split(File.pathSeparator)) {
      File dir = new File(pathEntry);
      if (!dir.isDirectory()) {
        continue;
      }
      for (String fileName : GRADLE_START_FILE_NAMES) {
        File startFile = new File(dir, fileName);
        if (startFile.isFile()) {
          File candidate = dir.getParentFile();
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
  @Nullable
  public File getGradleHomeFromEnvProperty(@Nullable Project project) {
    String path = System.getenv(GRADLE_ENV_PROPERTY_NAME);
    if (path == null) {
      return null;
    }
    File candidate = new File(path);
    return isGradleSdkHome(project, candidate) ? candidate : null;
  }

  /**
   * Does the same job as {@link #isGradleSdkHome(Project, File)} for the given virtual file.
   *
   * @param project current IDE project
   * @param file gradle installation home candidate
   * @return {@code true} if given file points to the gradle installation; {@code false} otherwise
   */
  public boolean isGradleSdkHome(@Nullable Project project, @Nullable VirtualFile file) {
    if (file == null) {
      return false;
    }
    return isGradleSdkHome(project, new File(file.getPath()));
  }

  public boolean isGradleSdkHome(@Nullable File file) {
    return isGradleSdkHome(null, file);
  }

  /**
   * Allows to answer if given virtual file points to the gradle installation root.
   *
   * @param project current IDE project
   * @param file gradle installation root candidate
   * @return {@code true} if we consider that given file actually points to the gradle installation root;
   * {@code false} otherwise
   */
  public boolean isGradleSdkHome(@Nullable Project project, @Nullable File file) {
    if (file == null) {
      return false;
    }
    if (project == null) {
      ProjectManager projectManager = ProjectManager.getInstance();
      Project[] openProjects = projectManager.getOpenProjects();
      project = openProjects.length > 0 ? openProjects[0] : projectManager.getDefaultProject();
    }
    for (ExternalSystemExecutionAware executionAware : ExternalSystemExecutionAware.getExtensions(GradleConstants.SYSTEM_ID)) {
      if (!(executionAware instanceof GradleExecutionAware)) continue;
      GradleExecutionAware gradleExecutionAware = (GradleExecutionAware)executionAware;
      if (gradleExecutionAware.isGradleInstallationHomeDir(project, file.getPath())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Allows to answer if given virtual file points to the gradle installation root.
   *
   * @param gradleHomePath gradle installation root candidate
   * @return {@code true} if we consider that given file actually points to the gradle installation root;
   * {@code false} otherwise
   */
  public boolean isGradleSdkHome(@Nullable Project project, String gradleHomePath) {
    return isGradleSdkHome(project, new File(gradleHomePath));
  }

  /**
   * Allows to answer if given files contain the one from gradle installation.
   *
   * @param files files to process
   * @return {@code true} if one of the given files is from the gradle installation; {@code false} otherwise
   */
  public boolean isGradleSdk(VirtualFile @Nullable ... files) {
    if (files == null) {
      return false;
    }
    File[] arg = new File[files.length];
    for (int i = 0; i < files.length; i++) {
      arg[i] = new File(files[i].getPath());
    }
    return isGradleSdk(arg);
  }

  private static boolean isGradleSdk(File @Nullable ... files) {
    return findGradleJar(files) != null;
  }

  @Nullable
  private static File findGradleJar(File @Nullable ... files) {
    if (files == null) {
      return null;
    }
    for (File file : files) {
      if (GRADLE_JAR_FILE_PATTERN.matcher(file.getName()).matches()) {
        return file;
      }
    }

    if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
      StringBuilder filesInfo = new StringBuilder();
      for (File file : files) {
        filesInfo.append(file.getAbsolutePath()).append(';');
      }
      if (filesInfo.length() > 0) {
        filesInfo.setLength(filesInfo.length() - 1);
      }
      GradleLog.LOG.info(String.format(
        "Gradle sdk check fails. Reason: no one of the given files matches gradle JAR pattern (%s). Files: %s",
        GRADLE_JAR_FILE_PATTERN.toString(), filesInfo
      ));
    }

    return null;
  }

  /**
   * Allows to ask for the classpath roots of the classes that are additionally provided by the gradle integration (e.g. gradle class
   * files, bundled groovy-all jar etc).
   *
   * @param project target project to use for gradle home retrieval
   * @return classpath roots of the classes that are additionally provided by the gradle integration (if any);
   * {@code null} otherwise
   */
  @Nullable
  public List<VirtualFile> getClassRoots(@Nullable Project project) {
    List<File> files = getClassRoots(project, null);
    if (files == null) return null;
    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    final JarFileSystem jarFileSystem = JarFileSystem.getInstance();
    return ContainerUtil.mapNotNull(files, file -> {
      final VirtualFile virtualFile = localFileSystem.refreshAndFindFileByIoFile(file);
      return virtualFile != null ? jarFileSystem.getJarRootForLocalFile(virtualFile) : null;
    });
  }

  @Nullable
  public List<File> getClassRoots(@Nullable Project project, @Nullable String rootProjectPath) {
    if (project == null) return null;

    if (rootProjectPath == null) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        rootProjectPath = ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath();
        List<File> result = findGradleSdkClasspath(project, rootProjectPath);
        if (!result.isEmpty()) return result;
      }
    }
    else {
      return findGradleSdkClasspath(project, rootProjectPath);
    }

    return null;
  }

  @Nullable
  public static String getGradleVersion(@Nullable String gradleHome) {
    if (gradleHome == null) return null;
    File libs = new File(gradleHome, "lib");
    if (!libs.isDirectory()) return null;

    File[] files = libs.listFiles();
    if (files != null) {
      for (File file : files) {
        final Matcher matcher = GRADLE_JAR_FILE_PATTERN.matcher(file.getName());
        if (matcher.matches()) {
          return matcher.group(2);
        }
      }
    }
    return null;
  }


  private List<File> findGradleSdkClasspath(Project project, String rootProjectPath) {
    List<File> result = new ArrayList<>();

    if (StringUtil.isEmpty(rootProjectPath)) return result;

    File gradleHome = getGradleHome(project, rootProjectPath);

    if (gradleHome == null || !gradleHome.isDirectory()) {
      return result;
    }

    File src = new File(gradleHome, "src");
    if (src.isDirectory()) {
      if (new File(src, "org").isDirectory()) {
        addRoots(result, src);
      }
      else {
        addRoots(result, src.listFiles());
      }
    }

    final Collection<File> libraries = getAllLibraries(gradleHome);
    if (libraries == null) {
      return result;
    }

    for (File file : libraries) {
      if (isGradleBuildClasspathLibrary(file)) {
        ContainerUtil.addIfNotNull(result, file);
      }
    }

    return result;
  }

  private static boolean isGradleBuildClasspathLibrary(File file) {
    String fileName = file.getName();
    return ANY_GRADLE_JAR_FILE_PATTERN.matcher(fileName).matches()
           || ANT_JAR_PATTERN.matcher(fileName).matches()
           || IVY_JAR_PATTERN.matcher(fileName).matches()
           || isGroovyJar(fileName);
  }

  private static void addRoots(@NotNull List<? super File> result, File @Nullable ... files) {
    if (files == null) return;
    for (File file : files) {
      if (file == null || !file.isDirectory()) continue;
      result.add(file);
    }
  }

  private static boolean isGroovyJar(@NotNull String name) {
    name = StringUtil.toLowerCase(name);
    return name.startsWith("groovy-all-") && name.endsWith(".jar") && !name.contains("src") && !name.contains("doc");
  }

  @Nullable
  public static GradleVersion guessGradleVersion(@NotNull GradleProjectSettings settings) {
    DistributionType distributionType = settings.getDistributionType();
    if (distributionType == null) return null;
    BuildLayoutParameters buildLayoutParameters;
    Project project = findProject(settings);
    if (project == null)  {
      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      buildLayoutParameters = new LocalBuildLayoutParameters(defaultProject, settings.getExternalProjectPath()) {
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

  @Nullable
  public static GradleVersion parseDistributionVersion(@NotNull String path) {
    path = StringUtil.substringAfterLast(path, "/");
    if (path == null) return null;

    path = StringUtil.substringAfterLast(path, "gradle-");
    if (path == null) return null;

    int i = path.lastIndexOf('-');
    if (i <= 0) return null;

    return getGradleVersionSafe(path.substring(0, i));
  }

  @Nullable
  public static GradleVersion getGradleVersionSafe(String gradleVersion) {
    try {
      return GradleVersion.version(gradleVersion);
    }
    catch (IllegalArgumentException e) {
      // GradleVersion.version(gradleVersion) might throw exception for custom Gradle versions
      // https://youtrack.jetbrains.com/issue/IDEA-216892
      return null;
    }
  }

  @Nullable
  private static Project findProject(@NotNull GradleProjectSettings settings) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      GradleProjectSettings linkedProjectSettings =
        GradleSettings.getInstance(project).getLinkedProjectSettings(settings.getExternalProjectPath());
      if (linkedProjectSettings == settings) {
        return project;
      }
    }
    return null;
  }
}
