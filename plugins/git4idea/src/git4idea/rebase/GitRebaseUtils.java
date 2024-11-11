// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.stash.GitChangesSaver;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static git4idea.GitNotificationIdsHolder.*;

public final class GitRebaseUtils {
  private static final Logger LOG = Logger.getInstance(GitRebaseUtils.class.getName());

  private GitRebaseUtils() {
  }

  public static void rebase(final @NotNull Project project,
                            final @NotNull List<? extends GitRepository> repositories,
                            final @NotNull GitRebaseParams params,
                            final @NotNull ProgressIndicator indicator) {
    if (!isRebaseAllowed(project, repositories)) return;  // TODO maybe move to the outside
    new GitRebaseProcess(project, GitRebaseSpec.forNewRebase(project, params, repositories, indicator), null).rebase();
  }

  public static void continueRebase(@NotNull Project project) {
    continueRebase(project, true);
  }

  static void continueRebaseWithoutFreezing(@NotNull Project project) {
    continueRebase(project, false);
  }

  private static void continueRebase(@NotNull Project project, boolean freeze) {
    GitRebaseSpec spec = GitUtil.getRepositoryManager(project).getOngoingRebaseSpec();
    if (spec != null) {
      GitRebaseProcess rebaseProcess = new GitRebaseProcess(project, spec, GitRebaseResumeMode.CONTINUE);
      if (freeze) {
        rebaseProcess.rebase();
      } else {
        rebaseProcess.doRebase();
      }
    }
    else {
      notifyContinueFailed(project, "continue");
    }
  }

  public static void continueRebase(@NotNull Project project, @NotNull GitRepository repository, @NotNull ProgressIndicator indicator) {
    GitRebaseSpec spec = GitRebaseSpec.forResumeInSingleRepository(project, repository, indicator);
    if (spec != null) {
      new GitRebaseProcess(project, spec, GitRebaseResumeMode.CONTINUE).rebase();
    }
    else {
      notifyContinueFailed(project, "continue");
    }
  }

  public static void skipRebase(@NotNull Project project) {
    GitRebaseSpec spec = GitUtil.getRepositoryManager(project).getOngoingRebaseSpec();
    if (spec != null) {
      new GitRebaseProcess(project, spec, GitRebaseResumeMode.SKIP).rebase();
    }
    else {
      notifyContinueFailed(project, "skip");
    }
  }

  public static void skipRebase(@NotNull Project project, @NotNull GitRepository repository, @NotNull ProgressIndicator indicator) {
    GitRebaseSpec spec = GitRebaseSpec.forResumeInSingleRepository(project, repository, indicator);
    if (spec != null) {
      new GitRebaseProcess(project, spec, GitRebaseResumeMode.SKIP).rebase();
    }
    else {
      notifyContinueFailed(project, "skip");
    }
  }

  private static void notifyContinueFailed(@NotNull Project project, @NotNull @NonNls String action) {
    LOG.warn(String.format("Refusing to %s: no rebase spec", action));
    VcsNotifier.getInstance(project).notifyError(
      REBASE_CANNOT_CONTINUE, GitBundle.message("rebase.notification.no.rebase.in.progress.continue.title"),
      GitBundle.message("rebase.notification.no.rebase.in.progress.message"),
      true
    );
  }

  /**
   * Automatically detects the ongoing rebase process in the project and abort it.
   * Optionally rollbacks repositories which were already rebased during that detected multi-root rebase process.
   * <p/>
   * Does nothing if no information about ongoing rebase is available, or if this information has become obsolete.
   */
  public static void abort(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    GitRebaseSpec spec = GitUtil.getRepositoryManager(project).getOngoingRebaseSpec();
    if (spec != null) {
      new GitAbortRebaseProcess(project, spec.getOngoingRebase(), spec.getHeadPositionsToRollback(), spec.getInitialBranchNames(),
                                indicator, spec.getSaver(), true).abortWithConfirmation();
    }
    else {
      LOG.warn("Refusing to abort: no rebase spec");
      VcsNotifier.getInstance(project).notifyError(
        REBASE_CANNOT_ABORT, GitBundle.message("rebase.notification.no.rebase.in.progress.abort.title"),
        GitBundle.message("rebase.notification.no.rebase.in.progress.message"),
        true
      );
    }
  }

  /**
   * Abort the ongoing rebase process in the given repository.
   */
  public static void abort(final @NotNull Project project, final @Nullable GitRepository repository, @NotNull ProgressIndicator indicator) {
    new GitAbortRebaseProcess(project, repository, Collections.emptyMap(),
                              Collections.emptyMap(), indicator, null, true).abortWithConfirmation();
  }

  private static boolean isRebaseAllowed(@NotNull Project project, @NotNull Collection<? extends GitRepository> repositories) {
    // TODO links to 'rebase', 'resolve conflicts', etc.
    for (GitRepository repository : repositories) {
      Repository.State state = repository.getState();
      String repositoryName = getShortRepositoryName(repository);
      String message = switch (state) {
        case NORMAL, DETACHED -> {
          if (repository.isFresh()) {
            yield GitBundle.message("rebase.notification.not.allowed.empty.repository.message", repositoryName);
          }
          yield null;
        }
        case MERGING -> new HtmlBuilder()
          .append(GitBundle.message("rebase.notification.not.allowed.merging.message.first", repositoryName)).br()
          .append(GitBundle.message("rebase.notification.not.allowed.merging.message.second"))
          .toString();
        case REBASING -> new HtmlBuilder()
          .append(GitBundle.message("rebase.notification.not.allowed.rebasing.message.first", repositoryName)).br()
          .append(GitBundle.message("rebase.notification.not.allowed.rebasing.message.second"))
          .toString();
        case GRAFTING -> new HtmlBuilder()
          .append(GitBundle.message("rebase.notification.not.allowed.grafting.message.first", repositoryName)).br()
          .append(GitBundle.message("rebase.notification.not.allowed.grafting.message.second"))
          .toString();
        case REVERTING -> new HtmlBuilder()
          .append(GitBundle.message("rebase.notification.not.allowed.reverting.message.first", repositoryName)).br()
          .append(GitBundle.message("rebase.notification.not.allowed.reverting.message.second"))
          .toString();
      };
      if (message != null) {
        VcsNotifier.getInstance(project).notifyError(
          REBASE_NOT_ALLOWED,
          GitBundle.message("rebase.notification.not.allowed.title"),
          message
        );
        return false;
      }
    }
    return true;
  }

  public static @Nullable File getRebaseDir(@NotNull Project project, @NotNull VirtualFile root) {
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRootQuick(root);
    if (repository == null) return null;

    File f = repository.getRepositoryFiles().getRebaseApplyDir();
    if (f.exists()) {
      return f;
    }
    f = repository.getRepositoryFiles().getRebaseMergeDir();
    if (f.exists()) {
      return f;
    }
    return null;
  }

  public static boolean isInteractiveRebaseInProgress(@NotNull GitRepository repository) {
    File rebaseDir = getRebaseDir(repository.getProject(), repository.getRoot());
    return rebaseDir != null && new File(rebaseDir, "interactive").exists();
  }

  /**
   * Get rebase directory
   *
   *
   * @param root the vcs root
   * @return the commit information or null if no commit information could be detected
   */
  public static @Nullable CommitInfo getCurrentRebaseCommit(@NotNull Project project, @NotNull VirtualFile root) {
    File rebaseDir = getRebaseDir(project, root);
    if (rebaseDir == null) {
      LOG.warn("No rebase dir found for " + root.getPath());
      return null;
    }
    File nextFile = new File(rebaseDir, "next");
    int next;
    try {
      next = Integer.parseInt(FileUtil.loadFile(nextFile, StandardCharsets.UTF_8).trim());
    }
    catch (Exception e) {
      LOG.warn("Failed to load next commit number from file " + nextFile.getPath(), e);
      return null;
    }
    File commitFile = new File(rebaseDir, String.format("%04d", next));
    String hash = null;
    String subject = null;
    try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(commitFile), StandardCharsets.UTF_8))) {
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
    catch (Exception e) {
      LOG.warn("Failed to load next commit number from file " + commitFile, e);
      return null;
    }
    if (subject == null || hash == null) {
      LOG.info("Unable to extract information from " + commitFile + " " + hash + ": " + subject);
      return null;
    }
    return new CommitInfo(new GitRevisionNumber(hash), subject);
  }

  static @NotNull @Nls String mentionLocalChangesRemainingInStash(@Nullable GitChangesSaver saver) {
    if (saver == null || !saver.wereChangesSaved()) {
      return "";
    }
    return new HtmlBuilder().br().append(saver.getSaveMethod().selectBundleMessage(
      GitBundle.message("rebase.notification.saved.local.changes.part.stash.text"),
      GitBundle.message("rebase.notification.saved.local.changes.part.shelf.text")
    )).toString();
  }

  public static @NotNull Collection<GitRepository> getRebasingRepositories(@NotNull Project project) {
    return GitUtil.getRepositoriesInStates(project, Repository.State.REBASING);
  }

  public static int getNumberOfCommitsToRebase(@NotNull GitRepository repository, @Nullable String upstream, @Nullable String branch)
    throws VcsException {

    String rebasingBranch = branch;
    if (rebasingBranch == null) {
      if (repository.isRebaseInProgress()) {
        rebasingBranch = getRebasingBranchHash(repository).asString();
      }
      else {
        rebasingBranch = GitUtil.HEAD;
      }
    }

    return GitHistoryUtils.collectTimedCommits(
      repository.getProject(),
      repository.getRoot(),
      getCommitsRangeToRebase(upstream, rebasingBranch)
    ).size();
  }

  private static @NotNull Hash getRebasingBranchHash(@NotNull GitRepository repository) throws VcsException {
    return readHashFromFile(repository.getProject(), repository.getRoot(), "orig-head");
  }

  public static @Nullable Hash getOntoHash(@NotNull Project project, @NotNull VirtualFile root) {
    try {
      return readHashFromFile(project, root, "onto");
    }
    catch (VcsException e) {
      return null;
    }
  }

  public static @NotNull String getCommitsRangeToRebase(@Nullable String baseBranch, @NotNull String rebasingBranch) {
    if (baseBranch == null) {
      return rebasingBranch;
    }
    return baseBranch + ".." + rebasingBranch;
  }

  private static @NotNull Hash readHashFromFile(
    @NotNull Project project,
    @NotNull VirtualFile root,
    @NotNull @NonNls String fileName
  ) throws VcsException {
    try {
      return HashImpl.build(FileUtil.loadFile(new File(getRebaseDir(project, root), fileName)).trim());
    }
    catch (IOException e) {
      throw new VcsException(GitBundle.message("rebase.couldnt.resolve.file", fileName), e);
    }
  }

  /**
   * @see git4idea.commands.GitImpl#REBASE_CONFIG_PARAMS
   */
  public static GitRebaseEditorHandler createRebaseEditor(@NotNull Project project,
                                                          @NotNull VirtualFile root,
                                                          boolean forInteractiveRebase) {
    GitInteractiveRebaseEditorHandler editor = new GitInteractiveRebaseEditorHandler(project, root);
    if (!forInteractiveRebase) {
      editor.setRebaseEditorShown();
    }
    return editor;
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
