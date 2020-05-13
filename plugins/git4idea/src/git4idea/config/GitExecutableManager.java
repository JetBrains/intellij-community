// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.VcsException;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.*;

import java.io.File;
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

  @Nullable private String myDetectedExecutable;
  @NotNull private final Object DETECTED_EXECUTABLE_LOCK = new Object();
  @NotNull private final CachingFileTester<GitVersion> myVersionCache;

  public GitExecutableManager() {
    myVersionCache = new CachingFileTester<GitVersion>() {
      @NotNull
      @Override
      protected GitVersion testFile(@NotNull String filePath) throws VcsException, ParseException {
        return doGetGitVersion(filePath);
      }
    };
  }

  private static GitVersion doGetGitVersion(@NotNull String pathToGit) throws VcsException, ParseException {
    LOG.debug("Acquiring git version for " + pathToGit);
    GitLineHandler handler = new GitLineHandler(null,
                                                new File("."),
                                                pathToGit,
                                                GitCommand.VERSION,
                                                Collections.emptyList());
    handler.setPreValidateExecutable(false);
    handler.setSilent(false);
    handler.setTerminationTimeout(1000);
    handler.setStdoutSuppressed(false);
    GitCommandResult result = Git.getInstance().runCommand(handler);
    String rawResult = result.getOutputOrThrow();
    GitVersion version = GitVersion.parse(rawResult);
    LOG.info("Git version for " + pathToGit + " : " + version.getPresentation());
    return version;
  }

  @NotNull
  public String getPathToGit() {
    String path = GitVcsApplicationSettings.getInstance().getSavedPathToGit();
    return path == null ? getDetectedExecutable() : path;
  }

  @NotNull
  public String getPathToGit(@NotNull Project project) {
    String path = GitVcsSettings.getInstance(project).getPathToGit();
    return path == null ? getPathToGit() : path;
  }

  @NotNull
  public String getDetectedExecutable() {
    synchronized (DETECTED_EXECUTABLE_LOCK) {
      if (myDetectedExecutable == null) {
        myDetectedExecutable = new GitExecutableDetector().detect();
      }
      return myDetectedExecutable;
    }
  }

  public void dropExecutableCache() {
    synchronized (DETECTED_EXECUTABLE_LOCK) {
      myDetectedExecutable = null;
    }
  }

  /**
   * Get version of git executable used in project
   *
   * @return actual version or {@link GitVersion#NULL} if version could not be identified or was not identified yet
   */
  @CalledInAny
  @NotNull
  public GitVersion getVersion(@NotNull Project project) {
    return getVersion(getPathToGit(project));
  }

  /**
   * Get version of git executable
   *
   * @return actual version or {@link GitVersion#NULL} if version could not be identified or was not identified yet
   */
  @CalledInAny
  @NotNull
  public GitVersion getVersion(@NotNull String executable) {
    CachingFileTester<GitVersion>.TestResult result = myVersionCache.getCachedResultForFile(executable);
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
      String pathToGit = getPathToGit(project);
      GitVersion version;
      try {
        version = identifyVersion(pathToGit);
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
        String pathToGit = getPathToGit(project);
        return identifyVersion(pathToGit);
      }
      catch (ProcessCanceledException e) {
        return null;
      }
      catch (GitVersionIdentificationException e) {
        return null;
      }
    });
  }

  private static <T> T runUnderProgressIfNeeded(@NotNull Project project,
                                                @NotNull String title,
                                                @NotNull ThrowableComputable<T, RuntimeException> task) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return ProgressManager.getInstance().runProcessWithProgressSynchronously(task, title, true, project);
    }
    else {
      return task.compute();
    }
  }

  /**
   * Try to identify version of git executable
   *
   * @param pathToGit path to executable file
   * @return version of git executable
   * @throws GitVersionIdentificationException if there is a problem running executable or parsing version output
   */
  @CalledInBackground
  @NotNull
  public GitVersion identifyVersion(@NotNull String pathToGit) throws GitVersionIdentificationException {
    CachingFileTester<GitVersion>.TestResult result = myVersionCache.getResultForFile(pathToGit);
    if (result.getResult() == null) {
      throw new GitVersionIdentificationException("Cannot identify version of git executable " + pathToGit, result.getException());
    }
    else {
      return result.getResult();
    }
  }

  public void dropVersionCache(@NotNull String pathToGit) {
    myVersionCache.dropCache(pathToGit);
  }

  /**
   * Check is executable used for project is valid, notify if it is not
   *
   * @return {@code true} is executable is valid, {@code false} otherwise
   */
  @CalledInBackground
  public boolean testGitExecutableVersionValid(@NotNull Project project) {
    String pathToGit = getPathToGit(project);
    GitVersion version = identifyVersionOrDisplayError(project, pathToGit);
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
  private GitVersion identifyVersionOrDisplayError(@NotNull Project project, @NotNull String pathToGit) {
    try {
      return identifyVersion(pathToGit);
    }
    catch (GitVersionIdentificationException e) {
      GitExecutableProblemsNotifier.getInstance(project).notifyExecutionError(e);
      return null;
    }
  }
}
