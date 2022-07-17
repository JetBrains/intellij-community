// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.google.common.collect.Maps;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import git4idea.GitCommit;
import git4idea.GitLocalBranch;
import git4idea.GitNotificationIdsHolder;
import git4idea.GitRemoteBranch;
import git4idea.commands.*;
import git4idea.config.GitSharedSettings;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION;
import static com.intellij.util.containers.ContainerUtil.exists;
import static git4idea.GitNotificationIdsHolder.BRANCH_DELETION_ROLLBACK_ERROR;
import static git4idea.util.GitUIUtil.bold;
import static git4idea.util.GitUIUtil.code;

/**
 * Deletes a branch.
 * If branch is not fully merged to the current branch, shows a dialog with the list of unmerged commits and with a list of branches
 * current branch are merged to, and makes force delete, if wanted.
 */
class GitDeleteBranchOperation extends GitBranchOperation {

  private static final Logger LOG = Logger.getInstance(GitDeleteBranchOperation.class);

  static final String RESTORE = getRestore();
  static final String VIEW_COMMITS = getViewCommits();
  static final String DELETE_TRACKED_BRANCH = getDeleteTrackedBranch();

  @NotNull private final String myBranchName;
  @NotNull private final VcsNotifier myNotifier;
  @NotNull private final Map<GitRepository, GitRemoteBranch> myTrackedBranches;

  @NotNull private final Map<GitRepository, UnmergedBranchInfo> myUnmergedToBranches;
  @NotNull private final Map<GitRepository, String> myDeletedBranchTips;

  GitDeleteBranchOperation(@NotNull Project project, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler,
                           @NotNull Collection<? extends GitRepository> repositories, @NotNull String branchName) {
    super(project, git, uiHandler, repositories);
    myBranchName = branchName;
    myNotifier = VcsNotifier.getInstance(myProject);
    myTrackedBranches = findTrackedBranches(repositories, branchName);
    myUnmergedToBranches = new HashMap<>();
    myDeletedBranchTips = ContainerUtil.map2MapNotNull(repositories, (GitRepository repo) -> {
      GitBranchesCollection branches = repo.getBranches();
      GitLocalBranch branch = branches.findLocalBranch(myBranchName);
      if (branch == null) {
        LOG.error("Couldn't find branch by name " + myBranchName + " in " + repo);
        return null;
      }
      Hash hash = branches.getHash(branch);
      if (hash == null) {
        LOG.error("Couldn't find hash for branch " + branch + " in " + repo);
        return null;
      }
      return Pair.create(repo, hash.asString());
    });
  }

  @Override
  public void execute() {
    boolean fatalErrorHappened = false;
    while (hasMoreRepositories() && !fatalErrorHappened) {
      final GitRepository repository = next();

      GitSimpleEventDetector notFullyMergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.BRANCH_NOT_FULLY_MERGED);
      GitBranchNotMergedToUpstreamDetector notMergedToUpstreamDetector = new GitBranchNotMergedToUpstreamDetector();
      GitCommandResult result = myGit.branchDelete(repository, myBranchName, false, notFullyMergedDetector, notMergedToUpstreamDetector);

      if (result.success()) {
        refresh(repository);
        markSuccessful(repository);
      }
      else if (notFullyMergedDetector.hasHappened()) {
        String baseBranch = notMergedToUpstreamDetector.getBaseBranch();
        if (baseBranch == null) { // GitBranchNotMergedToUpstreamDetector didn't happen
          baseBranch = myCurrentHeads.get(repository);
        }
        myUnmergedToBranches.put(repository, new UnmergedBranchInfo(myDeletedBranchTips.get(repository),
                                                                    GitBranchUtil.stripRefsPrefix(baseBranch)));

        GitCommandResult forceDeleteResult = myGit.branchDelete(repository, myBranchName, true);
        if (forceDeleteResult.success()) {
          refresh(repository);
          markSuccessful(repository);
        }
        else {
          fatalError(getErrorTitle(), forceDeleteResult);
          fatalErrorHappened = true;
        }
      }
      else {
        fatalError(getErrorTitle(), result);
        fatalErrorHappened = true;
      }
    }

    if (!fatalErrorHappened) {
      notifySuccess();
    }
  }

  @Override
  protected void notifySuccess() {
    boolean unmergedCommits = !myUnmergedToBranches.isEmpty();
    HtmlBuilder message = new HtmlBuilder().appendRaw(GitBundle.message("delete.branch.operation.deleted.branch.bold", myBranchName));
    if (unmergedCommits) {
      message.br().append(GitBundle.message("delete.branch.operation.unmerged.commits.were.discarded"));
    }

    Notification notification = STANDARD_NOTIFICATION.createNotification(message.toString(), NotificationType.INFORMATION);
    notification.setDisplayId(GitNotificationIdsHolder.BRANCH_DELETED);
    notification.addAction(NotificationAction.createSimple(() -> getRestore(), () -> {
      notification.expire();
      restoreInBackground(notification);
    }));
    if (unmergedCommits) {
      notification.addAction(NotificationAction.createSimple(() -> getViewCommits(), () -> viewUnmergedCommitsInBackground(notification)));
    }
    if (!myTrackedBranches.isEmpty() &&
        hasNoOtherTrackingBranch(myTrackedBranches, myBranchName) &&
        trackedBranchIsNotProtected()) {
      notification.addAction(NotificationAction.createSimple(() -> getDeleteTrackedBranch(), () -> {
        notification.expire();
        deleteTrackedBranchInBackground();
      }));
    }
    myNotifier.notify(notification);
  }

  private boolean trackedBranchIsNotProtected() {
    return myTrackedBranches.values().stream()
      .noneMatch(branch -> GitSharedSettings.getInstance(myProject).isBranchProtected(branch.getNameForRemoteOperations()));
  }

  private static boolean hasNoOtherTrackingBranch(@NotNull Map<GitRepository, GitRemoteBranch> trackedBranches,
                                                  @NotNull String localBranch) {
    for (GitRepository repository : trackedBranches.keySet()) {
      if (exists(repository.getBranchTrackInfos(), info -> !info.getLocalBranch().getName().equals(localBranch) &&
                                                           info.getRemoteBranch().equals(trackedBranches.get(repository)))) {
        return false;
      }
    }
    return true;
  }

  private static void refresh(GitRepository @NotNull ... repositories) {
    for (GitRepository repository : repositories) {
      repository.update();
    }
  }

  @Override
  protected void rollback() {
    GitCompoundResult result = doRollback();
    if (!result.totalSuccess()) {
      myNotifier.notifyError(BRANCH_DELETION_ROLLBACK_ERROR,
                             GitBundle.message("delete.branch.operation.error.during.rollback.of.branch.deletion"),
                             result.getErrorOutputWithReposIndication(),
                             true);
    }
  }

  @NotNull
  private GitCompoundResult doRollback() {
    GitCompoundResult result = new GitCompoundResult(myProject);
    for (GitRepository repository : getSuccessfulRepositories()) {
      GitCommandResult res = myGit.branchCreate(repository, myBranchName, myDeletedBranchTips.get(repository));
      result.append(repository, res);

      // restore tracking
      GitRemoteBranch trackedBranch = myTrackedBranches.get(repository);
      if (trackedBranch != null) {
        GitCommandResult setTrackResult = myGit.setUpstream(repository, trackedBranch.getNameForLocalOperations(), myBranchName);
        if (!setTrackResult.success()) {
          LOG.warn("Couldn't set " + myBranchName + " to track " + trackedBranch + " in " + repository.getRoot().getName() + ": " +
                   setTrackResult.getErrorOutputAsJoinedString());
        }
      }

      refresh(repository);
    }
    return result;
  }

  @NotNull
  @NlsContexts.NotificationTitle
  private String getErrorTitle() {
    return GitBundle.message("delete.branch.operation.branch.was.not.deleted.error", myBranchName);
  }

  @Override
  @NotNull
  protected String getSuccessMessage() {
    return GitBundle.message("delete.branch.operation.deleted.branch", formatBranchName(myBranchName));
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    return new HtmlBuilder().append(GitBundle.message("delete.branch.operation.however.branch.deletion.has.succeeded.for.the.following",
                                                      getSuccessfulRepositories().size()))
      .br()
      .appendRaw(successfulRepositoriesJoined())
      .br()
      .append(GitBundle.message("delete.branch.operation.you.may.rollback.not.to.let.branches.diverge", myBranchName))
      .toString();
  }

  @NotNull
  @Nls
  @Override
  protected String getOperationName() {
    return GitBundle.message("delete.branch.operation.name");
  }

  @NotNull
  private static String formatBranchName(@NotNull String name) {
    return bold(code(name));
  }

  /**
   * Shows a dialog "the branch is not fully merged" with the list of unmerged commits.
   * User may still want to force delete the branch.
   * In multi-repository setup collects unmerged commits for all given repositories.
   * @return true if the branch should be restored.
   */
  private boolean showNotFullyMergedDialog(@NotNull Map<GitRepository, UnmergedBranchInfo> unmergedBranches) {
    Map<GitRepository, List<GitCommit>> history = new HashMap<>();
    // we don't confuse user with the absence of repositories which branch was deleted w/o force,
    // we display no commits for them
    for (GitRepository repository : getRepositories()) {
      if (unmergedBranches.containsKey(repository)) {
        UnmergedBranchInfo unmergedInfo = unmergedBranches.get(repository);
        history.put(repository, getUnmergedCommits(repository, unmergedInfo.myTipOfDeletedUnmergedBranch, unmergedInfo.myBaseBranch));
      }
      else {
        history.put(repository, Collections.emptyList());
      }
    }
    Map<GitRepository, String> baseBranches = Maps.asMap(unmergedBranches.keySet(), it -> unmergedBranches.get(it).myBaseBranch);
    return myUiHandler.showBranchIsNotFullyMergedDialog(myProject, history, baseBranches, myBranchName);
  }

  @NotNull
  private static List<GitCommit> getUnmergedCommits(@NotNull GitRepository repository,
                                                    @NotNull String branchName,
                                                    @NotNull String baseBranch) {
    String range = baseBranch + ".." + branchName;
    try {
      return GitHistoryUtils.history(repository.getProject(), repository.getRoot(), range);
    }
    catch (VcsException e) {
      LOG.warn("Couldn't get `git log " + range + "` in " + getShortRepositoryName(repository), e);
    }
    return Collections.emptyList();
  }

  @NotNull
  private static Map<GitRepository, GitRemoteBranch> findTrackedBranches(@NotNull Collection<? extends GitRepository> repositories,
                                                                         @NotNull String localBranchName) {
    Map<GitRepository, GitRemoteBranch> trackedBranches = new HashMap<>();
    for (GitRepository repository : repositories) {
      GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfo(repository, localBranchName);
      if (trackInfo != null) trackedBranches.put(repository, trackInfo.getRemoteBranch());
    }
    return trackedBranches;
  }

  // warning: not deleting branch 'feature' that is not yet merged to
  //          'refs/remotes/origin/feature', even though it is merged to HEAD.
  // error: The branch 'feature' is not fully merged.
  // If you are sure you want to delete it, run 'git branch -D feature'.
  private static class GitBranchNotMergedToUpstreamDetector implements GitLineHandlerListener {

    private static final Pattern PATTERN = Pattern.compile(".*'(.*)', even though it is merged to.*");
    @Nullable private String myBaseBranch;

    @Override
    public void onLineAvailable(String line, Key outputType) {
      Matcher matcher = PATTERN.matcher(line);
      if (matcher.matches()) {
        myBaseBranch = matcher.group(1);
      }
    }

    @Nullable
    public String getBaseBranch() {
      return myBaseBranch;
    }
  }

  static class UnmergedBranchInfo {
    @NotNull private final String myTipOfDeletedUnmergedBranch;
    @NotNull private final String myBaseBranch;

    UnmergedBranchInfo(@NotNull String tipOfDeletedUnmergedBranch, @NotNull String baseBranch) {
      myTipOfDeletedUnmergedBranch = tipOfDeletedUnmergedBranch;
      myBaseBranch = baseBranch;
    }
  }

  private void deleteTrackedBranchInBackground() {
    GitBrancher brancher = GitBrancher.getInstance(myProject);
    MultiMap<String, GitRepository> grouped = groupTrackedBranchesByName();
    for (String remoteBranch : grouped.keySet()) {
      brancher.deleteRemoteBranch(remoteBranch, new ArrayList<>(grouped.get(remoteBranch)));
    }
  }

  @NotNull
  private MultiMap<String, GitRepository> groupTrackedBranchesByName() {
    MultiMap<String, GitRepository> trackedBranchNames = MultiMap.create();
    for (GitRepository repository : myTrackedBranches.keySet()) {
      GitRemoteBranch trackedBranch = myTrackedBranches.get(repository);
      if (trackedBranch != null) {
        trackedBranchNames.putValue(trackedBranch.getNameForLocalOperations(), repository);
      }
    }
    return trackedBranchNames;
  }

  private void restoreInBackground(@NotNull Notification notification) {
    new Task.Backgroundable(myProject, GitBundle.message("delete.branch.operation.restoring.branch.process", myBranchName)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        rollbackBranchDeletion(notification);
      }
    }.queue();
  }

  private void rollbackBranchDeletion(@NotNull Notification notification) {
    GitCompoundResult result = doRollback();
    if (result.totalSuccess()) {
      notification.expire();
    }
    else {
      myNotifier.notifyError(BRANCH_DELETION_ROLLBACK_ERROR,
                             GitBundle.message("delete.branch.operation.could.not.restore.branch.error", formatBranchName(myBranchName)),
                             result.getErrorOutputWithReposIndication(),
                             true);
    }
  }

  private void viewUnmergedCommitsInBackground(@NotNull Notification notification) {
    new Task.Backgroundable(myProject, GitBundle.message("delete.branch.operation.collecting.unmerged.commits.process")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        boolean restore = showNotFullyMergedDialog(myUnmergedToBranches);
        if (restore) {
          rollbackBranchDeletion(notification);
        }
      }
    }.queue();
  }

  @NotNull
  @Nls
  static String getRestore() {
    return GitBundle.message("action.NotificationAction.GitDeleteBranchOperation.text.restore");
  }

  @NotNull
  @Nls
  static String getViewCommits() {
    return GitBundle.message("action.NotificationAction.GitDeleteBranchOperation.text.view.commits");
  }

  @NotNull
  @Nls
  static String getDeleteTrackedBranch() {
    return GitBundle.message("action.NotificationAction.GitDeleteBranchOperation.text.delete.tracked.branch");
  }
}
