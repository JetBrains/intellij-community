// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitRevisionNumber;
import git4idea.GitTag;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

import static com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION;

/**
 * Deletes tag.
 */
class GitDeleteTagOperation extends GitBranchOperation {

  private static final Logger LOG = Logger.getInstance(GitDeleteTagOperation.class);

  private static final String RESTORE = "Restore";

  @NotNull private final String myTagName;
  @NotNull private final VcsNotifier myNotifier;

  @NotNull private final Map<GitRepository, String> myDeletedTagTips;

  GitDeleteTagOperation(@NotNull Project project, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler,
                        @NotNull Collection<GitRepository> repositories, @NotNull String tagName) {
    super(project, git, uiHandler, repositories);
    myTagName = tagName;
    myNotifier = VcsNotifier.getInstance(myProject);
    myDeletedTagTips = ContainerUtil.map2MapNotNull(repositories, (GitRepository repo) -> {
      try {
        GitRevisionNumber revisionNumber = GitRevisionNumber.resolve(myProject, repo.getRoot(), GitTag.REFS_TAGS_PREFIX + tagName);
        return Pair.create(repo, revisionNumber.asString());
      }
      catch (VcsException e) {
        LOG.error("Couldn't find hash for tag " + myTagName + " in " + repo);
        return null;
      }
    });
  }

  @Override
  public void execute() {
    while (hasMoreRepositories()) {
      GitRepository repository = next();
      GitCommandResult result = myGit.deleteTag(repository, myTagName);
      if (result.success()) {
        repository.getRepositoryFiles().refresh();
        markSuccessful(repository);
      }
      else {
        fatalError(String.format("Tag %s wasn't deleted", myTagName), result.getErrorOutputAsJoinedString());
        return;
      }
    }
    notifySuccess();
  }

  @Override
  protected void notifySuccess() {
    String message = "<b>Deleted Tag:</b> " + myTagName;
    Notification notification = STANDARD_NOTIFICATION.createNotification("", message, NotificationType.INFORMATION, null);
    notification.addAction(NotificationAction.createSimple(RESTORE, () -> restoreInBackground(notification)));
    myNotifier.notify(notification);
  }

  private void restoreInBackground(@NotNull Notification notification) {
    new Task.Backgroundable(myProject, "Restoring Tag " + myTagName + "...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        rollbackTagDeletion(notification);
      }
    }.queue();
  }

  private void rollbackTagDeletion(@NotNull Notification notification) {
    GitCompoundResult result = new GitCompoundResult(myProject);
    for (GitRepository repository: getSuccessfulRepositories()) {
      GitCommandResult res = myGit.createNewTag(repository, myTagName, null, myDeletedTagTips.get(repository));
      result.append(repository, res);
      repository.getRepositoryFiles().refresh();
    }

    if (result.totalSuccess()) {
      notification.expire();
    }
    else {
      myNotifier.notifyError("Couldn't Restore <b><code>" + myTagName + "</code></b>", result.getErrorOutputWithReposIndication());
    }
  }

  @Override
  protected void rollback() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected String getOperationName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public String getSuccessMessage() {
    throw new UnsupportedOperationException();
  }
}
