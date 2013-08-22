package org.jetbrains.plugins.gradle.service;

import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleEnvironment;
import org.jetbrains.plugins.gradle.util.GradleLog;
import org.jetbrains.plugins.gradle.util.GradleUtil;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Encapsulates algorithm of gradle libraries discovery.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 8/4/11 11:06 AM
 */
@SuppressWarnings("MethodMayBeStatic")
public class GradleInstallationManager {

  public static final Pattern  GRADLE_JAR_FILE_PATTERN;
  public static final Pattern  ANY_GRADLE_JAR_FILE_PATTERN;

  private static final String[] GRADLE_START_FILE_NAMES;
  @NonNls private static final String GRADLE_ENV_PROPERTY_NAME;
  static {
    // Init static data with ability to redefine it locally.
    GRADLE_JAR_FILE_PATTERN = Pattern.compile(System.getProperty("gradle.pattern.core.jar", "gradle-(core-)?(\\d.*)\\.jar"));
    ANY_GRADLE_JAR_FILE_PATTERN = Pattern.compile(System.getProperty("gradle.pattern.core.jar", "gradle-(.*)\\.jar"));
    GRADLE_START_FILE_NAMES = System.getProperty("gradle.start.file.names", "gradle:gradle.cmd:gradle.sh").split(":");
    GRADLE_ENV_PROPERTY_NAME = System.getProperty("gradle.home.env.key", "GRADLE_HOME");
  }
  
  @NotNull private final PlatformFacade myPlatformFacade;
  @Nullable private Ref<File> myCachedGradleHomeFromPath;

  public GradleInstallationManager(@NotNull PlatformFacade facade) {
    myPlatformFacade = facade;
  }

  /**
   * Allows to get file handles for the gradle binaries to use.
   *
   * @param project            target project
   * @param linkedProjectPath  path to the target external project's config
   * @return                   file handles for the gradle binaries; <code>null</code> if gradle is not discovered
   */
  @Nullable
  public Collection<File> getAllLibraries(@Nullable Project project, @NotNull String linkedProjectPath) {

    // Manually defined gradle home
    File gradleHome = getGradleHome(project, linkedProjectPath);

    if (gradleHome == null || !gradleHome.isDirectory()) {
      return null;
    }

    List<File> result = ContainerUtilRt.newArrayList();
    
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

  /**
   * Tries to return file handle that points to the gradle installation home.
   *
   * @param project            target project (if any)
   * @param linkedProjectPath  path to the target linked project config
   * @return                   file handle that points to the gradle installation home (if any)
   */
  @Nullable
  public File getGradleHome(@Nullable Project project, @NotNull String linkedProjectPath) {
    File result = getWrapperHome(project, linkedProjectPath);
    if (result != null) {
      return result;
    }
    result = getManuallyDefinedGradleHome(project, linkedProjectPath);
    if (result != null) {
      return result;
    }
    return getAutodetectedGradleHome();
  }

  /**
   * Tries to deduce gradle location from current environment.
   *
   * @return gradle home deduced from the current environment (if any); <code>null</code> otherwise
   */
  @Nullable
  public File getAutodetectedGradleHome() {
    File result = getGradleHomeFromPath();
    return result == null ? getGradleHomeFromEnvProperty() : result;
  }

  /**
   * Tries to return gradle home that is defined as a dependency to the given module.
   *
   * @param module  target module
   * @return file handle that points to the gradle installation home defined as a dependency of the given module (if any)
   */
  @Nullable
  public VirtualFile getGradleHome(@Nullable Module module) {
    if (module == null) {
      return null;
    }
    final VirtualFile[] roots = OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots();
    if (roots == null) {
      return null;
    }
    for (VirtualFile root : roots) {
      if (root != null && isGradleSdkHome(root)) {
        return root;
      }
    }
    return null;
  }

  /**
   * Tries to return gradle home defined as a dependency of the given module; falls back to the project-wide settings otherwise.
   *
   * @param module   target module that can have gradle home as a dependency
   * @param project  target project which gradle home setting should be used if module-specific gradle location is not defined
   * @return         gradle home derived from the settings of the given entities (if any); <code>null</code> otherwise
   */
  @Nullable
  public VirtualFile getGradleHome(@Nullable Module module, @Nullable Project project, @NotNull String linkedProjectPath) {
    final VirtualFile result = getGradleHome(module);
    if (result != null) {
      return result;
    }

    final File home = getGradleHome(project, linkedProjectPath);
    return home == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByIoFile(home);
  }

  @Nullable
  public File getWrapperHome(@Nullable Project project, @NotNull String linkedProjectPath) {
    if (project == null) {
      return null;
    }

    GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath);
    if (settings == null) {
      return null;
    }
    
    if (settings.isPreferLocalInstallationToWrapper()) {
      return null;
    }

    String distribution = GradleUtil.getWrapperDistribution(linkedProjectPath);
    if (distribution == null) {
      return null;
    }
    File gradleSystemDir = new File(System.getProperty("user.home"), ".gradle");
    if (!gradleSystemDir.isDirectory()) {
      return null;
    }

    File gradleWrapperDistributionsHome = new File(gradleSystemDir, "wrapper/dists");
    if (!gradleWrapperDistributionsHome.isDirectory()) {
      return null;
    }

    File targetDistributionHome = new File(gradleWrapperDistributionsHome, distribution);
    if (!targetDistributionHome.isDirectory()) {
      return null;
    }

    File[] files = targetDistributionHome.listFiles();
    if (files == null || files.length != 1) {
      // Gradle keeps wrapper at a directory which name is a hash value like '35oej0jnbfh6of4dd05531edaj'
      return null;
    }

    File[] distFiles = files[0].listFiles(new FileFilter() {
      @Override
      public boolean accept(File f) {
        return f.isDirectory();
      }
    });
    if (distFiles == null || distFiles.length != 1) {
      // There should exist only the gradle directory in the distribution directory
      return null;
    }

    return distFiles[0].isDirectory() ? distFiles[0] : null;
  }

  /**
   * Allows to ask for user-defined path to gradle.
   *
   * @param project  target project to use (if any)
   * @return         path to the gradle distribution (if the one is explicitly configured)
   */
  @Nullable
  public File getManuallyDefinedGradleHome(@Nullable Project project, @NotNull String linkedProjectPath) {
    if (project == null) {
      return null;
    }
    GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath);
    if (settings == null) {
      return null;
    }
    
    String path = settings.getGradleHome();
    if (path == null) {
      return null;
    }
    File candidate = new File(path);
    return isGradleSdkHome(candidate) ? candidate : null;
  }

  /**
   * Tries to discover gradle installation path from the configured system path
   *
   * @return    file handle for the gradle directory if it's possible to deduce from the system path; <code>null</code> otherwise
   */
  @Nullable
  public File getGradleHomeFromPath() {
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
          if (isGradleSdkHome(candidate)) {
            myCachedGradleHomeFromPath = new Ref<File>(candidate);
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
   * @return    file handle for the gradle directory deduced from the system property (if any)
   */
  @Nullable
  public File getGradleHomeFromEnvProperty() {
    String path = System.getenv(GRADLE_ENV_PROPERTY_NAME);
    if (path == null) {
      return null;
    }
    File candidate = new File(path);
    return isGradleSdkHome(candidate) ? candidate : null;
  }

  /**
   * Does the same job as {@link #isGradleSdkHome(File)} for the given virtual file.
   *
   * @param file  gradle installation home candidate
   * @return      <code>true</code> if given file points to the gradle installation; <code>false</code> otherwise
   */
  public boolean isGradleSdkHome(@Nullable VirtualFile file) {
    if (file == null) {
      return false;
    }
    return isGradleSdkHome(new File(file.getPath()));
  }

  /**
   * Allows to answer if given virtual file points to the gradle installation root.
   *
   * @param file  gradle installation root candidate
   * @return      <code>true</code> if we consider that given file actually points to the gradle installation root;
   *              <code>false</code> otherwise
   */
  public boolean isGradleSdkHome(@Nullable File file) {
    if (file == null) {
      return false;
    }
    final File libs = new File(file, "lib");
    if (!libs.isDirectory()) {
      if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
        GradleLog.LOG.info(String.format(
          "Gradle sdk check failed for the path '%s'. Reason: it doesn't have a child directory named 'lib'", file.getAbsolutePath()
        ));
      }
      return false;
    }

    final boolean found = isGradleSdk(libs.listFiles());
    if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
      GradleLog.LOG.info(String.format("Gradle home check %s for the path '%s'", found ? "passed" : "failed", file.getAbsolutePath()));
    }
    return found;
  }

  /**
   * Allows to answer if given files contain the one from gradle installation.
   *
   * @param files  files to process
   * @return       <code>true</code> if one of the given files is from the gradle installation; <code>false</code> otherwise
   */
  public boolean isGradleSdk(@Nullable VirtualFile... files) {
    if (files == null) {
      return false;
    }
    File[] arg = new File[files.length];
    for (int i = 0; i < files.length; i++) {
      arg[i] = new File(files[i].getPath());
    }
    return isGradleSdk(arg);
  }

  private boolean isGradleSdk(@Nullable File ... files) {
    return findGradleJar(files) != null;
  }

  @Nullable
  private File findGradleJar(@Nullable File ... files) {
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
        "Gradle sdk check fails. Reason: no one of the given files matches gradle jar pattern (%s). Files: %s",
        GRADLE_JAR_FILE_PATTERN.toString(), filesInfo
      ));
    }

    return null;
  }

  /**
   * Allows to ask for the classpath roots of the classes that are additionally provided by the gradle integration (e.g. gradle class
   * files, bundled groovy-all jar etc).
   *
   * @param project  target project to use for gradle home retrieval
   * @return         classpath roots of the classes that are additionally provided by the gradle integration (if any);
   *                 <code>null</code> otherwise
   */
  @Nullable
  public List<VirtualFile> getClassRoots(@Nullable Project project) {
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    for (Module module : myPlatformFacade.getModules(project)) {
      String path = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
      if (StringUtil.isEmpty(path)) {
        continue;
      }
      assert path != null;
      final Collection<File> libraries = getAllLibraries(project, path);
      if (libraries == null) {
        continue;
      }
      final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
      final JarFileSystem jarFileSystem = JarFileSystem.getInstance();
      List<VirtualFile> result = new ArrayList<VirtualFile>();
      for (File file : libraries) {
        if (ANY_GRADLE_JAR_FILE_PATTERN.matcher(file.getName()).matches() || GroovyConfigUtils.matchesGroovyAll(file.getName())) {
          final VirtualFile virtualFile = localFileSystem.refreshAndFindFileByIoFile(file);
          if (virtualFile != null) {
            ContainerUtil.addIfNotNull(result, jarFileSystem.getJarRootForLocalFile(virtualFile));
          }
        }
      }
      return result;
    }
    return null;
  }
}
