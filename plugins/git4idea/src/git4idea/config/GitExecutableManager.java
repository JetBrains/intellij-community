// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.ParseException;
import java.util.Collections;

/**
 * Manager for "current git executable".
 * Allows to get a path to git executable and executable version.
 */
public class GitExecutableManager {
  public static GitExecutableManager getInstance() {
    return ServiceManager.getService(GitExecutableManager.class);
  }

  @NotNull private final GitVcsApplicationSettings myApplicationSettings;
  @NotNull private final AtomicNotNullLazyValue<String> myDetectedExecutable;
  @NotNull private final CachingFileTester<GitVersion> myVersionCache;

  public GitExecutableManager(@NotNull GitVcsApplicationSettings applicationSettings) {
    myApplicationSettings = applicationSettings;
    myDetectedExecutable = AtomicNotNullLazyValue.createValue(new GitExecutableDetector()::detect);
    myVersionCache = new CachingFileTester<GitVersion>() {
      @NotNull
      @Override
      protected GitVersion testFile(@NotNull String filePath) throws VcsException, ParseException {
        return doGetGitVersion(filePath);
      }
    };
  }

  private static GitVersion doGetGitVersion(@NotNull String pathToGit) throws VcsException, ParseException {
    GitLineHandler handler = new GitLineHandler(null,
                                                new File("."),
                                                pathToGit,
                                                GitCommand.VERSION,
                                                Collections.emptyList());
    GitCommandResult result = Git.getInstance().runCommand(handler);
    return GitVersion.parse(result.getOutputOrThrow());
  }

  @NotNull
  public String getPathToGit() {
    String path = myApplicationSettings.getSavedPathToGit();
    return path == null ? getDetectedExecutable() : path;
  }

  @NotNull
  public String getPathToGit(@NotNull Project project) {
    String path = GitVcsSettings.getInstance(project).getPathToGit();
    return path == null ? getPathToGit() : path;
  }

  @NotNull
  public String getDetectedExecutable() {
    return myDetectedExecutable.getValue();
  }

  /**
   * Get version of git executable used in project
   *
   * @return actual version or {@link GitVersion#NULL} if version could not be identified or was not identified yet
   */
  @CalledInAny
  @NotNull
  public GitVersion getVersion(@NotNull Project project) {
    String projectExecutablePath = getPathToGit(project);
    CachingFileTester<GitVersion>.TestResult result = myVersionCache.getCachedResultForFile(projectExecutablePath);
    if (result == null || result.getResult() == null) {
      return GitVersion.NULL;
    }
    else {
      return result.getResult();
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

  /**
   * Check is executable used for project is valid, notify if it is not
   *
   * @return {@code true} is executable is valid, {@code false} otherwise
   */
  @CalledInBackground
  public boolean testGitExecutableVersionValid(@NotNull Project project) {
    GitExecutableProblemsNotifier executableProblemsNotifier = GitExecutableProblemsNotifier.getInstance(project);
    GitVersion version = getGitVersionAndNotifyErrors(getPathToGit(project), executableProblemsNotifier);
    if (version == null) {
      return false;
    }
    else if (!version.isSupported()) {
      executableProblemsNotifier.notifyUnsupportedVersion(version);
      return false;
    }
    return true;
  }

  @Nullable
  private GitVersion getGitVersionAndNotifyErrors(@NotNull String pathToGit, @NotNull GitExecutableProblemsNotifier notifier) {
    try {
      GitVersion version = identifyVersion(pathToGit);
      if (version.isSupported()) {
        notifier.expireNotifications();
      }
      return version;
    }
    catch (GitVersionIdentificationException e) {
      notifier.notifyExecutionError(pathToGit, e);
      return null;
    }
  }
}
