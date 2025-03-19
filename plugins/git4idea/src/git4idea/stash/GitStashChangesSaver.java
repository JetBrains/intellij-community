// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.history.ActivityId;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import git4idea.GitActivity;
import git4idea.GitStashUsageCollector;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.i18n.GitBundle;
import git4idea.index.GitStageManagerKt;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.GitUnstashDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static git4idea.GitNotificationIdsHolder.UNSTASH_WITH_CONFLICTS;
import static git4idea.stash.ui.GitStashContentProviderKt.isStashTabAvailable;
import static git4idea.stash.ui.GitStashContentProviderKt.showStashes;

public class GitStashChangesSaver extends GitChangesSaver {

  private static final Logger LOG = Logger.getInstance(GitStashChangesSaver.class);
  private static final String NO_LOCAL_CHANGES_TO_SAVE = "No local changes to save";

  private final @NotNull GitRepositoryManager myRepositoryManager;
  private final @NotNull Map<VirtualFile, /* @Nullable */ Hash> myStashedRoots = new HashMap<>(); // stashed roots & nullable stash commit

  private boolean myReportLocalHistoryActivity = true;

  public GitStashChangesSaver(@NotNull Project project,
                              @NotNull Git git,
                              @NotNull ProgressIndicator progressIndicator,
                              @NotNull String stashMessage) {
    super(project, git, progressIndicator, GitSaveChangesPolicy.STASH, stashMessage);
    myRepositoryManager = GitUtil.getRepositoryManager(project);
  }

  @Override
  protected void save(@NotNull Collection<? extends VirtualFile> rootsToSave) throws VcsException {
    LOG.info("saving " + rootsToSave);

    ActivityId activityId = myReportLocalHistoryActivity ? GitActivity.Stash : null;
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, GitBundle.message("activity.name.stash"), activityId)) {
      for (VirtualFile root : rootsToSave) {
        String message = GitBundle.message("stash.progress.indicator.title", root.getName());
        LOG.info(message);
        final String oldProgressTitle = myProgressIndicator.getText();
        myProgressIndicator.setText(message);
        GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
        if (repository == null) {
          LOG.error("Repository is null for root " + root);
        }
        else {
          StructuredIdeActivity activity = GitStashUsageCollector.logStashPush(myProject);
          GitCommandResult result = myGit.stashSave(repository, myStashMessage);
          activity.finished();

          if (result.success() && somethingWasStashed(result)) {
            myStashedRoots.put(root, myGit.resolveReference(repository, "stash@{0}"));
          }
          else {
            if (!result.success()) {
              throw new VcsException(GitBundle.message("exception.message.could.not.stash.root.error",
                                                       repository.getRoot(), result.getErrorOutputAsJoinedString()));
            }
            else {
              LOG.warn("There was nothing to stash in " + repository.getRoot());
            }
          }
        }
        myProgressIndicator.setText(oldProgressTitle);
      }
    }
  }

  private static boolean somethingWasStashed(@NotNull GitCommandResult result) {
    return !StringUtil.containsIgnoreCase(result.getErrorOutputAsJoinedString(), NO_LOCAL_CHANGES_TO_SAVE) &&
           !StringUtil.containsIgnoreCase(result.getOutputAsJoinedString(), NO_LOCAL_CHANGES_TO_SAVE);
  }

  @Override
  public void load() {
    final String oldProgressTitle = myProgressIndicator.getText();
    GitStashOperations.unstash(myProject, myStashedRoots, (root) -> {
      String message = GitBundle.message("stash.unstash.progress.indicator.title", root.getName());
      myProgressIndicator.setText(message);
      GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.STASH);
      handler.addParameters("pop");
      if (GitStageManagerKt.isStagingAreaAvailable(myProject)) {
        handler.addParameters("--index");
      }
      return handler;
    }, new UnstashConflictResolver(myProject, myGit, myStashedRoots.keySet(), myParams), myReportLocalHistoryActivity);
    myProgressIndicator.setText(oldProgressTitle);
  }

  @Override
  public boolean wereChangesSaved() {
    return !myStashedRoots.isEmpty();
  }

  @Override
  public void showSavedChanges() {
    VirtualFile firstRoot = ContainerUtil.getFirstItem(myStashedRoots.keySet());
    if (isStashTabAvailable()) {
      showStashes(myProject, firstRoot);
    } else {
      GitUnstashDialog.showUnstashDialog(myProject, new ArrayList<>(myStashedRoots.keySet()), firstRoot);
    }
  }

  void setReportLocalHistoryActivity(boolean reportLocalHistoryActivity) {
    myReportLocalHistoryActivity = reportLocalHistoryActivity;
  }

  @Override
  public String toString() {
    return "StashChangesSaver. Roots: " + myStashedRoots;
  }

  private static class UnstashConflictResolver extends GitConflictResolver {

    private final Set<? extends VirtualFile> myStashedRoots;

    UnstashConflictResolver(@NotNull Project project, @NotNull Git git,
                            @NotNull Set<? extends VirtualFile> stashedRoots, @Nullable Params params) {
      super(project, stashedRoots, makeParamsOrUse(params, project));
      myStashedRoots = stashedRoots;
    }

    private static Params makeParamsOrUse(@Nullable Params givenParams, Project project) {
      if (givenParams != null) {
        return givenParams;
      }
      Params params = new Params(project);
      params.setErrorNotificationTitle(GitBundle.message("preserving.process.local.changes.not.restored.error.title"));
      params.setMergeDialogCustomizer(new UnstashMergeDialogCustomizer());
      params.setReverse(true);
      return params;
    }


    @Override
    protected void notifyUnresolvedRemain() {
      VcsNotifier.importantNotification()
        .createNotification(GitBundle.message("stash.unstash.unresolved.conflict.warning.notification.title"),
                            GitBundle.message("stash.unstash.unresolved.conflict.warning.notification.message"),
                            NotificationType.WARNING)
        .setDisplayId(UNSTASH_WITH_CONFLICTS)
        .addAction(NotificationAction.createSimple(
          GitBundle.messagePointer("stash.unstash.unresolved.conflict.warning.notification.show.stash.action"), () -> {
            VirtualFile firstRoot = ContainerUtil.getFirstItem(myStashedRoots);
            if (isStashTabAvailable()) {
              showStashes(myProject, firstRoot);
            } else {
              // we don't use #showSavedChanges to specify unmerged root first
              GitUnstashDialog.showUnstashDialog(myProject, new ArrayList<>(myStashedRoots), firstRoot);
            }
          }))
        .addAction(NotificationAction.createSimple(
          GitBundle.messagePointer("stash.unstash.unresolved.conflict.warning.notification.resolve.conflicts.action"), () -> {
            mergeNoProceedInBackground();
          }))
        .notify(myProject);
    }
  }

  private static class UnstashMergeDialogCustomizer extends MergeDialogCustomizer {

    @Override
    public @NotNull String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
      return GitBundle.message("stash.unstash.conflict.dialog.description.label.text");
    }

    @Override
    public @NotNull String getLeftPanelTitle(@NotNull VirtualFile file) {
      return getConflictLeftPanelTitle();
    }

    @Override
    public @NotNull String getRightPanelTitle(@NotNull VirtualFile file, VcsRevisionNumber revisionNumber) {
      return getConflictRightPanelTitle();
    }
  }
}
