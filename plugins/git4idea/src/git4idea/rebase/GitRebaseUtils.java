/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryFiles;
import git4idea.stash.GitChangesSaver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The utilities related to rebase functionality
 */
public class GitRebaseUtils {
  /**
   * The logger instance
   */
  private final static Logger LOG = Logger.getInstance(GitRebaseUtils.class.getName());

  /**
   * A private constructor for utility class
   */
  private GitRebaseUtils() {
  }

  public static void rebase(@NotNull final Project project,
                            @NotNull final List<GitRepository> repositories,
                            @NotNull final GitRebaseParams params,
                            @NotNull final ProgressIndicator indicator) {
    if (!isRebaseAllowed(project, repositories)) return;  // TODO maybe move to the outside
    new GitRebaseProcess(project, repositories, params, indicator).rebase();
  }

  /**
   * Abort the ongoing rebase process in the {@code repositoryToAbort},
   * and optionally rollback rebase which has already successfully completed in some other repositories.
   *
   * @param repositoryToAbort      Repository to perform {@code git rebase --abort}.
   * @param repositoriesToRollback Repositories to rollback the successful rebase, together with commit hashes which were HEAD revisions
   *                               before that successful rebase started - these are the revisions which the method will rollback to
   *                               via {@code git reset --keep}.
   */
  public static void abort(@NotNull final Project project,
                           @Nullable final GitRepository repositoryToAbort,
                           @NotNull final Map<GitRepository, String> repositoriesToRollback,
                           @NotNull ProgressIndicator progressIndicator) {
    new GitAbortRebaseProcess(project, repositoryToAbort, repositoriesToRollback, progressIndicator, null).abortWithConfirmation();
  }

  private static boolean isRebaseAllowed(@NotNull Project project, @NotNull Collection<GitRepository> repositories) {
    // TODO links to 'rebase', 'resolve conflicts', etc.
    for (GitRepository repository : repositories) {
      Repository.State state = repository.getState();
      String in = GitUtil.mention(repository);
      String message = null;
      switch (state) {
        case NORMAL:
          if (repository.isFresh()) {
            message = "Repository" + in + " is empty.";
          }
          break;
        case MERGING:
          message = "There is an unfinished merge process" + in + ".<br/>You should complete the merge before starting a rebase";
          break;
        case REBASING:
          message = "There is an unfinished rebase process" + in + ".<br/>You should complete it before starting another rebase";
          break;
        case GRAFTING:
          message = "There is an unfinished cherry-pick process" + in + ".<br/>You should finish it before starting a rebase.";
          break;
        case DETACHED:
          message = "You are in the detached HEAD state" + in + ".<br/>Rebase is not possible.";
          break;
        default:
          LOG.error("Unknown state [" + state.name() + "]");
          message = "Rebase is not possible" + in;
      }
      if (message != null) {
        VcsNotifier.getInstance(project).notifyError("Rebase not Allowed", message);
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if the rebase is in the progress for the specified git root
   *
   * @param root the git root
   * @return true if the rebase directory presents in the root
   */
  @Deprecated
  public static boolean isRebaseInTheProgress(VirtualFile root) {
    return getRebaseDir(root) != null;
  }

  /**
   * Get rebase directory
   *
   * @param root the vcs root
   * @return the rebase directory or null if it does not exist.
   */
  @Nullable
  private static File getRebaseDir(VirtualFile root) {
    File gitDir = new File(VfsUtilCore.virtualToIoFile(root), GitUtil.DOT_GIT);
    File f = new File(gitDir, GitRepositoryFiles.REBASE_APPLY);
    if (f.exists()) {
      return f;
    }
    f = new File(gitDir, GitRepositoryFiles.REBASE_MERGE);
    if (f.exists()) {
      return f;
    }
    return null;
  }

  /**
   * Get rebase directory
   *
   * @param root the vcs root
   * @return the commit information or null if no commit information could be detected
   */
  @Nullable
  public static CommitInfo getCurrentRebaseCommit(VirtualFile root) {
    File rebaseDir = getRebaseDir(root);
    if (rebaseDir == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("No rebase dir found for " + root.getPath());
      }
      return null;
    }
    File nextFile = new File(rebaseDir, "next");
    int next;
    try {
      next = Integer.parseInt(FileUtil.loadFile(nextFile, CharsetToolkit.UTF8_CHARSET).trim());
    }
    catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Failed to load next commit number from file " + nextFile.getPath(), e);
      }
      return null;
    }
    File commitFile = new File(rebaseDir, String.format("%04d", next));
    String hash = null;
    String subject = null;
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(commitFile), CharsetToolkit.UTF8_CHARSET));
      try {
        String line;
        while ((line = in.readLine()) != null) {
          if (line.startsWith("From ")) {
            hash = line.substring(5, 5 + 40);
          }
          if (line.startsWith("Subject: ")) {
            subject = line.substring("Subject: ".length());
          }
          if (hash != null && subject != null) {
            break;
          }
        }
      }
      finally {
        in.close();
      }
    }
    catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Failed to load next commit number from file " + commitFile, e);
      }
      return null;
    }
    if (subject == null || hash == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Unable to extract information from " + commitFile + " " + hash + ": " + subject);
      }
      return null;
    }
    return new CommitInfo(new GitRevisionNumber(hash), subject);
  }

  @NotNull
  static String mentionLocalChangesRemainingInStash(@Nullable GitChangesSaver saver) {
    return saver != null && saver.wereChangesSaved() ?
           "<br/>Note that some local changes were <a href='stash'>" + toPast(saver.getOperationName()) + "</a> before rebase." :
           "";
  }

  @NotNull
  private static String toPast(@NotNull String word) {
    return word.endsWith("e") ? word + "d" : word + "ed";
  }

  /**
   * Short commit info
   */
  public static class CommitInfo {
    /**
     * The commit hash
     */
    public final GitRevisionNumber revision;
    /**
     * The commit subject
     */
    public final String subject;

    /**
     * The constructor
     *
     * @param revision
     * @param subject the commit subject
     */
    public CommitInfo(GitRevisionNumber revision, String subject) {
      this.revision = revision;
      this.subject = subject;
    }

    @Override
    public String toString() {
      return revision.toString();
    }
  }
}
