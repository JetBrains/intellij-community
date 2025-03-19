// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitConfigurationCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static git4idea.config.GitExecutableManager.runUnderProgressIfNeeded;

/**
 * Tries to detect the path to Git executable.
 */
public class GitExecutableDetector {

  private static final Logger LOG = Logger.getInstance(GitExecutableDetector.class);
  private static final @NonNls String[] UNIX_PATHS = {
    "/usr/local/bin",
    "/opt/local/bin",
    "/usr/bin",
    "/opt/bin",
    "/usr/local/git/bin"};

  private static final @NonNls String[] APPLEGIT_PATHS = {
    "/usr/bin/git"
  };
  private static final @NonNls String[] APPLEGIT_DEPENDENCY_PATHS = {
    "/Library/Developer/CommandLineTools/usr/bin/git",
    "/Library/Preferences/com.apple.dt.Xcode"
  };

  private static final List<String> WIN_BIN_DIRS = Arrays.asList("cmd", "bin");

  private static final @NonNls String UNIX_EXECUTABLE = "git";
  private static final @NonNls String WIN_EXECUTABLE = "git.exe";

  private static final int WSL_DETECTION_TIMEOUT_MS = 10000;
  private final ScheduledExecutorService myWslExecutor =
    AppExecutorUtil.createBoundedScheduledExecutorService("GitExecutableDetector WSL thread", 1);

  private final @NotNull Object DETECTED_EXECUTABLE_LOCK = new Object();
  private final @NotNull AtomicReference<DetectionResult> myEnvExecutable = new AtomicReference<>();
  private final @NotNull AtomicReference<DetectionResult> mySystemExecutable = new AtomicReference<>();
  private final @NotNull Map<WSLDistribution, DetectionResult> myWslExecutables = new ConcurrentHashMap<>();
  private volatile boolean myWslDistributionsProcessed;

  /**
   * Default choice if detection failed - just an executable name to be resolved by $PATH.
   */
  public static @NotNull String getDefaultExecutable() {
    return SystemInfo.isWindows ? WIN_EXECUTABLE : UNIX_EXECUTABLE;
  }

  public @Nullable String getExecutable(@Nullable WSLDistribution projectWslDistribution, boolean detectIfNeeded) {
    if (detectIfNeeded) {
      return detect(projectWslDistribution);
    }
    else {
      return getCachedExecutable(projectWslDistribution);
    }
  }

  private @Nullable String getCachedExecutable(@Nullable WSLDistribution projectWslDistribution) {
    List<Detector> detectors = collectDetectors(projectWslDistribution);
    return getExecutable(detectors);
  }

  public @NotNull String detect(@Nullable WSLDistribution distribution) {
    List<Detector> detectors = collectDetectors(distribution);

    String detectedPath = getExecutable(detectors);
    if (detectedPath != null) return detectedPath;

    return runUnderProgressIfNeeded(null, GitBundle.message("git.executable.detect.progress.title"),
                                    () -> detectExecutable(detectors));
  }

  @RequiresBackgroundThread
  private @NotNull String detectExecutable(@NotNull List<Detector> detectors) {
    String path = null;
    boolean fireEvent = false;
    synchronized (DETECTED_EXECUTABLE_LOCK) {
      for (Detector detector : detectors) {
        DetectionResult detectionResult = detector.getPath();
        if (detectionResult == null) {
          detector.runDetection();
          fireEvent = true;

          detectionResult = detector.getPath();
        }

        if (detectionResult != null && detectionResult.detectedPath != null) {
          path = detectionResult.detectedPath;
          break;
        }
      }
    }

    if (fireEvent) {
      fireExecutableChanged();
    }

    if (path != null) return path;
    return getDefaultExecutable();
  }

  @RequiresBackgroundThread
  public void clear() {
    synchronized (DETECTED_EXECUTABLE_LOCK) {
      myEnvExecutable.set(null);
      mySystemExecutable.set(null);
      myWslExecutables.clear();
      myWslDistributionsProcessed = false;
    }
    fireExecutableChanged();
  }

  static void fireExecutableChanged() {
    GitConfigurationCache.getInstance().clearCache();
    ApplicationManager.getApplication().getMessageBus().syncPublisher(GitExecutableManager.TOPIC).executableChanged();
  }

  /**
   * @return 'null' if detection was not finished yet. Otherwise, return our best guess.
   */
  private static @Nullable String getExecutable(@NotNull List<Detector> detectors) {
    for (Detector detector : detectors) {
      DetectionResult path = detector.getPath();
      if (path == null) return null; // not detected yet
      if (path.detectedPath != null) return path.detectedPath;
    }
    return getDefaultExecutable();
  }

  private @NotNull List<Detector> collectDetectors(@Nullable WSLDistribution projectWslDistribution) {
    List<Detector> detectors = new ArrayList<>();
    if (projectWslDistribution != null &&
        GitExecutableManager.supportWslExecutable()) {
      detectors.add(new WslDetector(projectWslDistribution));
    }

    detectors.add(new EnvDetector());
    if (SystemInfo.isWindows) {
      detectors.add(new WinSystemPathDetector());
    }
    else {
      detectors.add(new UnixSystemPathDetector());
    }

    if (projectWslDistribution == null &&
        GitExecutableManager.supportWslExecutable() &&
        Registry.is("git.detect.wsl.executables")) {
      detectors.add(new GlobalWslDetector());
    }

    return detectors;
  }


  private interface Detector {
    /**
     * @return 'null' if detection was not completed yet.
     */
    @Nullable DetectionResult getPath();

    void runDetection();
  }

  private class EnvDetector implements Detector {
    @Override
    public @Nullable DetectionResult getPath() {
      return myEnvExecutable.get();
    }

    @Override
    public void runDetection() {
      String executableName = SystemInfo.isWindows ? WIN_EXECUTABLE : UNIX_EXECUTABLE;
      File executableFromEnv = PathEnvironmentVariableUtil.findInPath(executableName, getPathEnv(), null);
      String path = executableFromEnv != null ? executableFromEnv.getAbsolutePath() : null;
      myEnvExecutable.set(new DetectionResult(path));
    }
  }

  private class UnixSystemPathDetector implements Detector {
    @Override
    public @Nullable DetectionResult getPath() {
      return mySystemExecutable.get();
    }

    @Override
    public void runDetection() {
      String executable = detectForUnix();
      mySystemExecutable.set(new DetectionResult(executable));
    }

    private static @Nullable String detectForUnix() {
      for (String p : UNIX_PATHS) {
        File f = new File(p, UNIX_EXECUTABLE);
        if (f.exists()) {
          return f.getPath();
        }
      }
      return null;
    }
  }

  private class WinSystemPathDetector implements Detector {
    @Override
    public @Nullable DetectionResult getPath() {
      return mySystemExecutable.get();
    }

    @Override
    public void runDetection() {
      String executable = detectForWindows();
      mySystemExecutable.set(new DetectionResult(executable));
    }

    private @Nullable String detectForWindows() {
      String exec = checkProgramFiles();
      if (exec != null) {
        return exec;
      }

      exec = checkAppLocal();
      if (exec != null) {
        return exec;
      }

      exec = checkCygwin();
      if (exec != null) {
        return exec;
      }

      return null;
    }

    private @Nullable String checkProgramFiles() {
      File winRootInTests = getWinRootInTests();
      File winRoot = ObjectUtils.chooseNotNull(winRootInTests, new File("C:\\"));

      String programFiles = System.getenv("PROGRAMFILES");
      String programFilesX86 = System.getenv("PROGRAMFILES(X86)");

      File programFilesFile = !StringUtil.isEmpty(programFiles) && winRootInTests == null ?
                              new File(programFiles) :
                              new File(winRoot, "Program Files");

      File programFilesX86File = !StringUtil.isEmpty(programFilesX86) && winRootInTests == null ?
                                 new File(programFilesX86) :
                                 new File(winRoot, "Program Files (x86)");

      List<File> dirsToCheck = List.of(programFilesFile, programFilesX86File);

      // collecting all potential msys distributives
      List<File> distrs = new ArrayList<>();
      for (File dir : dirsToCheck) {
        distrs.addAll(findGitDistrsIn(dir));
      }

      return getPreferredDistrExecutablePath(distrs);
    }

    private static @Nullable String checkAppLocal() {
      String appLocal = System.getenv("LocalAppData");
      if (StringUtil.isEmpty(appLocal)) return null;

      File appLocalFile = new File(appLocal + "\\Programs");
      List<File> dirsToCheck = List.of(appLocalFile);

      // collecting all potential msys distributives
      List<File> distrs = new ArrayList<>();
      for (File dir : dirsToCheck) {
        distrs.addAll(findGitDistrsIn(dir));
      }

      return getPreferredDistrExecutablePath(distrs);
    }

    private @Nullable String checkCygwin() {
      File winRootInTests = getWinRootInTests();
      File winRoot = ObjectUtils.chooseNotNull(winRootInTests, new File("C:\\"));

      File defaultCygwinExe = new File(winRoot, "cygwin/bin/git.exe");

      List<File> exeToCheck = List.of(defaultCygwinExe);

      for (File file : exeToCheck) {
        if (file.exists()) {
          return file.getPath();
        }
      }
      return null;
    }

    private static @NotNull List<File> findGitDistrsIn(@NotNull File dir) {
      File[] children = dir.listFiles(pathname -> pathname.isDirectory() && StringUtil.toLowerCase(pathname.getName()).startsWith("git"));
      if (!dir.exists() || children == null) {
        return Collections.emptyList();
      }
      return Arrays.asList(children);
    }

    private static @Nullable String getPreferredDistrExecutablePath(@NotNull List<File> distrs) {
      // greater is better => sorting in the descending order to match the best version first, when iterating
      distrs.sort(Collections.reverseOrder(new VersionDirsComparator()));

      for (File distr : distrs) {
        String exec = getDistrExecutablePath(distr);
        if (exec != null) {
          return exec;
        }
      }
      return null;
    }

    /**
     * @param distr folder with git distribution, "C:\Program Files\Git"
     */
    private static @Nullable String getDistrExecutablePath(@Nullable File distr) {
      if (distr == null || !distr.exists()) {
        return null;
      }

      for (String binDirName : WIN_BIN_DIRS) {
        File binDir = new File(distr, binDirName);
        if (!binDir.exists()) continue;

        File fe = new File(binDir, WIN_EXECUTABLE);
        if (fe.exists()) {
          return fe.getPath();
        }
      }

      return null;
    }
  }

  private class WslDetector implements Detector {
    private final WSLDistribution myDistribution;

    private WslDetector(@NotNull WSLDistribution distribution) {
      myDistribution = distribution;
    }

    @Override
    public @Nullable DetectionResult getPath() {
      return myWslExecutables.get(myDistribution);
    }

    @Override
    public void runDetection() {
      String result = checkWslDistributionSafe(myDistribution);
      myWslExecutables.put(myDistribution, new DetectionResult(result));
    }
  }

  private class GlobalWslDetector implements Detector {
    @Override
    public @Nullable DetectionResult getPath() {
      if (!myWslDistributionsProcessed) return null;

      List<String> knownDistros = ContainerUtil.mapNotNull(myWslExecutables.values(), it -> it.detectedPath);
      if (knownDistros.size() != 1) return new DetectionResult(null);

      String path = knownDistros.iterator().next();
      return new DetectionResult(path);
    }

    @Override
    public void runDetection() {
      List<WSLDistribution> distributions = WslDistributionManager.getInstance().getInstalledDistributions();
      for (WSLDistribution distribution : distributions) {
        String result = checkWslDistributionSafe(distribution);
        myWslExecutables.put(distribution, new DetectionResult(result));
      }
      myWslDistributionsProcessed = true;
    }
  }

  private static class DetectionResult {
    public final @Nullable String detectedPath;

    private DetectionResult(@Nullable String path) {
      detectedPath = path;
    }
  }

  private static @Nullable String checkWslDistribution(@NotNull WSLDistribution distribution) {
    Path root = distribution.getUNCRootPath();
    for (String p : UNIX_PATHS) {
      Path f = root.resolve(p).resolve(UNIX_EXECUTABLE);
      if (Files.exists(f)) {
        return f.toString();
      }
    }
    return null;
  }

  /**
   * Guard against potential lock in OS code while accessing paths under WSL distro
   */
  private String checkWslDistributionSafe(@NotNull WSLDistribution distribution) {
    Future<String> future = myWslExecutor.submit(() -> checkWslDistribution(distribution));
    try {
      return future.get(WSL_DETECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      LOG.warn(String.format("WSL executable detection aborted for %s", distribution), e);
      future.cancel(true);
      return null;
    }
  }

  @VisibleForTesting
  protected @Nullable File getWinRootInTests() {
    return null;
  }

  @VisibleForTesting
  protected @Nullable String getPathEnv() {
    return PathEnvironmentVariableUtil.getPathVariableValue();
  }

  public static @Nullable String patchExecutablePath(@NotNull String path) {
    if (SystemInfo.isWindows) {
      File file = new File(path.trim());
      if (file.getName().equals("git-cmd.exe") || file.getName().equals("git-bash.exe")) {
        File patchedFile = new File(file.getParent(), "bin/git.exe");
        if (patchedFile.exists()) return patchedFile.getPath();
      }
    }
    return null;
  }

  public static @Nullable String getBashExecutablePath(@NotNull String gitExecutable) {
    if (!SystemInfo.isWindows) return null;

    File gitFile = new File(gitExecutable.trim());
    File gitDirFile = gitFile.getParentFile();
    if (gitDirFile != null && WIN_BIN_DIRS.contains(gitDirFile.getName())) {
      File bashFile = new File(gitDirFile.getParentFile(), "bin/bash.exe");
      if (bashFile.exists()) return bashFile.getPath();
    }
    return null;
  }

  public static @NotNull List<Path> getDependencyPaths(@NotNull Path executablePath) {
    try {
      if (SystemInfo.isMac && ArrayUtil.contains(executablePath.toString(), APPLEGIT_PATHS)) {
        return ContainerUtil.map(APPLEGIT_DEPENDENCY_PATHS, path -> Paths.get(path));
      }
    }
    catch (InvalidPathException e) {
      LOG.warn(e);
    }
    return Collections.emptyList();
  }

  // Compare strategy: greater is better (if v1 > v2, then v1 is a better candidate for the Git executable)
  private static class VersionDirsComparator implements Comparator<File> {

    @Override
    public int compare(File f1, File f2) {
      String name1 = StringUtil.toLowerCase(f1.getName());
      String name2 = StringUtil.toLowerCase(f2.getName());

      // C:\Program Files\Git is better candidate for _default_ than C:\Program Files\Git_1.8.0
      if (name1.equals("git")) {
        return name2.equals("git") ? fallback(f1, f2) : 1;
      }
      else if (name2.equals("git")) {
        return -1;
      }

      final Pattern GIT_WITH_VERSION = Pattern.compile("^git[ _]*([\\d\\.]*).*$");
      Matcher m1 = GIT_WITH_VERSION.matcher(name1);
      Matcher m2 = GIT_WITH_VERSION.matcher(name2);
      if (m1.matches() && m2.matches()) {
        GitVersion v1 = parseGitVersion(m1.group(1));
        GitVersion v2 = parseGitVersion(m2.group(1));
        if (v1 == null || v2 == null) {
          return fallback(f1, f2);
        }
        int compareVersions = v1.compareTo(v2);
        return compareVersions == 0 ? fallback(f1, f2) : compareVersions;
      }
      return fallback(f1, f2);
    }

    private static int fallback(@NotNull File f1, @NotNull File f2) {
      // "Program Files" is preferable over "Program Files (x86)"
      int compareParents = f1.getParentFile().getName().compareTo(f2.getParentFile().getName());
      if (compareParents != 0) {
        return -compareParents; // greater is better => reversing
      }

      // probably some unrecognized format of Git directory naming => just compare lexicographically
      String name1 = StringUtil.toLowerCase(f1.getName());
      String name2 = StringUtil.toLowerCase(f2.getName());
      return name1.compareTo(name2);
    }

    // not using GitVersion#parse(), because it requires at least 3 items in the version (1.7.3),
    // and parses the `git version` command output, not just the version string.
    private static @Nullable GitVersion parseGitVersion(@Nullable String name) {
      if (name == null) {
        return null;
      }
      final Pattern VERSION = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?.*");
      Matcher m = VERSION.matcher(name);
      if (!m.matches()) {
        return null;
      }
      try {
        int major = Integer.parseInt(m.group(1));
        return new GitVersion(major, parseOrNull(m.group(2)), parseOrNull(m.group(3)), parseOrNull(m.group(4)));
      }
      catch (NumberFormatException e) {
        LOG.info("Unexpected NFE when parsing [" + name + "]", e);
        return null;
      }
    }

    private static int parseOrNull(String group) {
      return group == null ? 0 : Integer.parseInt(group);
    }
  }
}
