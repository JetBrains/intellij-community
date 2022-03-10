// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import git4idea.GitStashUsageCollector;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.GitUnstashDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

import static git4idea.GitNotificationIdsHolder.UNSTASH_WITH_CONFLICTS;

public class GitStashChangesSaver extends GitChangesSaver {

  private static final Logger LOG = Logger.getInstance(GitStashChangesSaver.class);
  private static final String NO_LOCAL_CHANGES_TO_SAVE = "No local changes to save";

  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final Map<VirtualFile, /* @Nullable */ Hash> myStashedRoots = new HashMap<>(); // stashed roots & nullable stash commit

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
      return handler;
    }, new UnstashConflictResolver(myProject, myGit, myStashedRoots.keySet(), myParams));
    myProgressIndicator.setText(oldProgressTitle);
  }

  @Override
  public boolean wereChangesSaved() {
    return !myStashedRoots.isEmpty();
  }

  @Override
  public void showSavedChanges() {
    GitUnstashDialog.showUnstashDialog(myProject, new ArrayList<>(myStashedRoots.keySet()), myStashedRoots.keySet().iterator().next());
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
      VcsNotifier.getInstance(myProject).notifyImportantWarning(
        UNSTASH_WITH_CONFLICTS, GitBundle.message("stash.unstash.unresolved.conflict.warning.notification.title"),
        GitBundle.message("stash.unstash.unresolved.conflict.warning.notification.message"),
        new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              if (event.getDescription().equals("saver")) {
                // we don't use #showSavedChanges to specify unmerged root first
                GitUnstashDialog.showUnstashDialog(myProject, new ArrayList<>(myStashedRoots), myStashedRoots.iterator().next());
              }
              else if (event.getDescription().equals("resolve")) {
                mergeNoProceedInBackground();
              }
            }
          }
        }
      );
    }
  }

  private static class UnstashMergeDialogCustomizer extends MergeDialogCustomizer {

    @NotNull
    @Override
    public String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
      return GitBundle.message("stash.unstash.conflict.dialog.description.label.text");
    }

    @NotNull
    @Override
    public String getLeftPanelTitle(@NotNull VirtualFile file) {
      return getConflictLeftPanelTitle();
    }

    @NotNull
    @Override
    public String getRightPanelTitle(@NotNull VirtualFile file, VcsRevisionNumber revisionNumber) {
      return getConflictRightPanelTitle();
    }
  }
}
