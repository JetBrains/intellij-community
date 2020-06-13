// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThreeState;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.*;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.text.ParseException;
import java.util.Collections;

import static git4idea.config.GitExecutableProblemHandlersKt.showUnsupportedVersionError;

/**
 * Manager for "current git executable".
 * Allows to get a path to git executable and executable version.
 */
public class GitExecutableManager {
  public static GitExecutableManager getInstance() {
    return ServiceManager.getService(GitExecutableManager.class);
  }

  private static final Logger LOG = Logger.getInstance(GitExecutableManager.class);

  @NotNull private final GitExecutableDetector myExecutableDetector = new GitExecutableDetector();
  @NotNull private final CachingFileTester<GitVersion> myVersionCache;

  public GitExecutableManager() {
    myVersionCache = new CachingFileTester<GitVersion>() {
      @NotNull
      @Override
      protected GitVersion testExecutable(@NotNull GitExecutable executable) throws VcsException, ParseException {
        return doGetGitVersion(executable);
      }
    };
  }

  private static GitVersion doGetGitVersion(@NotNull GitExecutable executable) throws VcsException, ParseException {
    GitVersion.Type type = null;
    if (executable instanceof GitExecutable.Wsl) {
      WSLDistribution distribution = ((GitExecutable.Wsl)executable).getDistribution();
      type = WSLUtil.isWsl1(distribution) == ThreeState.YES ? GitVersion.Type.WSL1
                                                            : GitVersion.Type.WSL2;
    }

    LOG.debug("Acquiring git version for " + executable);
    GitLineHandler handler = new GitLineHandler(null,
                                                new File("."),
                                                executable,
                                                GitCommand.VERSION,
                                                Collections.emptyList());
    handler.setPreValidateExecutable(false);
    handler.setSilent(false);
    handler.setTerminationTimeout(1000);
    handler.setStdoutSuppressed(false);
    GitCommandResult result = Git.getInstance().runCommand(handler);
    String rawResult = result.getOutputOrThrow();
    GitVersion version = GitVersion.parse(rawResult, type);
    LOG.info("Git version for " + executable + ": " + version.toString());
    return version;
  }

  @NotNull
  public String getPathToGit() {
    return getPathToGit(null);
  }

  @NotNull
  public String getPathToGit(@Nullable Project project) {
    String pathToGit = getPathToGit(project, true);
    if (pathToGit == null) pathToGit = GitExecutableDetector.getDefaultExecutable();
    return pathToGit;
  }

  @Nullable
  private String getPathToGit(@Nullable Project project, boolean detectIfNeeded) {
    String path = project != null ? GitVcsSettings.getInstance(project).getPathToGit() : null;
    if (path == null) path = GitVcsApplicationSettings.getInstance().getSavedPathToGit();
    if (path == null) path = getDetectedExecutable(project, detectIfNeeded);
    return path;
  }

  @NotNull
  public GitExecutable getExecutable(@Nullable Project project) {
    String path = getPathToGit(project);
    return getExecutable(path);
  }

  @NotNull
  public GitExecutable getExecutable(@NotNull String pathToGit) {
    GitExecutable.Wsl executable = getWslExecutable(pathToGit);
    if (executable != null) return executable;

    return new GitExecutable.Local(pathToGit);
  }

  public static boolean supportWslExecutable() {
    return WSLUtil.isSystemCompatible() && Experiments.getInstance().isFeatureEnabled("wsl.p9.show.roots.in.file.chooser");
  }

  @Nullable
  private static GitExecutable.Wsl getWslExecutable(@NotNull String pathToGit) {
    Pair<String, WSLDistribution> pair = parseWslPath(pathToGit);
    return pair != null ? new GitExecutable.Wsl(pair.first, pair.second) : null;
  }

  @Nullable
  private static WSLDistribution getProjectWslDistribution(@Nullable Project project) {
    if (project == null) return null;
    String basePath = project.getBasePath();
    if (basePath == null) return null;

    Pair<String, WSLDistribution> pair = parseWslPath(FileUtil.toSystemDependentName(basePath));
    return pair != null ? pair.second : null;
  }

  @Nullable
  private static Pair<String, WSLDistribution> parseWslPath(@NotNull String path) {
    if (!supportWslExecutable()) return null;
    if (!path.startsWith(WSLDistribution.UNC_PREFIX)) return null;

    path = StringUtil.trimStart(path, WSLDistribution.UNC_PREFIX);
    int index = path.indexOf('\\');
    if (index == -1) return null;

    String distName = path.substring(0, index);
    String wslPath = FileUtil.toSystemIndependentName(path.substring(index));

    WSLDistribution distribution = WSLUtil.getDistributionByMsId(distName);
    if (distribution == null) return null;
    return Pair.create(wslPath, distribution);
  }

  @NotNull
  public String getDetectedExecutable(@Nullable Project project) {
    String executable = getDetectedExecutable(project, true);
    return executable != null ? executable : GitExecutableDetector.getDefaultExecutable();
  }

  @Nullable
  private String getDetectedExecutable(@Nullable Project project, boolean detectIfNeeded) {
    WSLDistribution distribution = getProjectWslDistribution(project);
    if (detectIfNeeded) {
      return myExecutableDetector.detect(distribution);
    }
    else {
      return myExecutableDetector.getExecutable(distribution);
    }
  }

  public void dropExecutableCache() {
    myExecutableDetector.clear();
  }

  /**
   * Get version of git executable used in project
   *
   * @return actual version or {@link GitVersion#NULL} if version could not be identified or was not identified yet
   */
  @CalledInAny
  @NotNull
  public GitVersion getVersion(@NotNull Project project) {
    String pathToGit = getPathToGit(project, false);
    if (pathToGit == null) return GitVersion.NULL;

    GitExecutable executable = getExecutable(pathToGit);
    return getVersion(executable);
  }

  /**
   * Get version of git executable
   *
   * @return actual version or {@link GitVersion#NULL} if version could not be identified or was not identified yet
   */
  @CalledInAny
  @NotNull
  public GitVersion getVersion(@NotNull GitExecutable executable) {
    CachingFileTester<GitVersion>.TestResult result = myVersionCache.getCachedResultFor(executable);
    if (result == null || result.getResult() == null) {
      return GitVersion.NULL;
    }
    else {
      return result.getResult();
    }
  }

  /**
   * Get version of git executable used in project or tell user that it cannot be obtained and cancel the operation
   * Version identification is done under progress because it can hang in rare cases
   * Usually this takes milliseconds because version is cached
   */
  @CalledInAwt
  @NotNull
  public GitVersion getVersionUnderModalProgressOrCancel(@NotNull Project project) throws ProcessCanceledException {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      GitExecutable executable = getExecutable(project);
      GitVersion version;
      try {
        version = identifyVersion(executable);
      }
      catch (GitVersionIdentificationException e) {
        throw new ProcessCanceledException();
      }
      return version;
    }, GitBundle.getString("git.executable.version.progress.title"), true, project);
  }

  @CalledInAny
  @Nullable
  public GitVersion tryGetVersion(@NotNull Project project) {
    return runUnderProgressIfNeeded(project, GitBundle.getString("git.executable.version.progress.title"), () -> {
      try {
        GitExecutable executable = getExecutable(project);
        return identifyVersion(executable);
      }
      catch (ProcessCanceledException e) {
        return null;
      }
      catch (GitVersionIdentificationException e) {
        return null;
      }
    });
  }

  static <T> T runUnderProgressIfNeeded(@Nullable Project project,
                                        @NotNull String title,
                                        @NotNull ThrowableComputable<T, RuntimeException> task) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return ProgressManager.getInstance().runProcessWithProgressSynchronously(task, title, true, project);
    }
    else {
      return task.compute();
    }
  }

  @CalledInBackground
  @NotNull
  public GitVersion identifyVersion(@NotNull String pathToGit) throws GitVersionIdentificationException {
    return identifyVersion(getExecutable(pathToGit));
  }

  /**
   * Try to identify version of git executable
   *
   * @throws GitVersionIdentificationException if there is a problem running executable or parsing version output
   */
  @CalledInBackground
  @NotNull
  public GitVersion identifyVersion(@NotNull GitExecutable executable) throws GitVersionIdentificationException {
    CachingFileTester<GitVersion>.TestResult result = myVersionCache.getResultFor(executable);
    if (result.getResult() == null) {
      Exception e = result.getException();
      if (e instanceof NoSuchFileException && executable.getExePath().equals(GitExecutableDetector.getDefaultExecutable())) {
        throw new GitNotInstalledException(GitBundle.message("executable.error.git.not.installed"), e);
      }
      throw new GitVersionIdentificationException(GitBundle.message("git.executable.validation.cant.identify.executable.message", executable), e);
    }
    else {
      return result.getResult();
    }
  }

  public void dropVersionCache(@NotNull GitExecutable executable) {
    myVersionCache.dropCache(executable);
  }

  /**
   * Check is executable used for project is valid, notify if it is not
   *
   * @return {@code true} is executable is valid, {@code false} otherwise
   */
  @CalledInBackground
  public boolean testGitExecutableVersionValid(@NotNull Project project) {
    GitExecutable executable = getExecutable(project);
    GitVersion version = identifyVersionOrDisplayError(project, executable);
    if (version == null) return false;

    GitExecutableProblemsNotifier executableProblemsNotifier = GitExecutableProblemsNotifier.getInstance(project);
    if (version.isSupported()) {
      executableProblemsNotifier.expireNotifications();
      return true;
    }
    else {
      showUnsupportedVersionError(project, version, new NotificationErrorNotifier(project));
      return false;
    }
  }

  @CalledInBackground
  @Nullable
  private GitVersion identifyVersionOrDisplayError(@NotNull Project project, @NotNull GitExecutable executable) {
    try {
      return identifyVersion(executable);
    }
    catch (GitVersionIdentificationException e) {
      GitExecutableProblemsNotifier.getInstance(project).notifyExecutionError(e);
      return null;
    }
  }
}
