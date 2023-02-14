// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitNotificationIdsHolder;
import git4idea.GitRevisionNumber;
import git4idea.GitTag;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION;
import static git4idea.GitNotificationIdsHolder.TAG_DELETION_ROLLBACK_ERROR;
import static git4idea.util.GitUIUtil.bold;
import static git4idea.util.GitUIUtil.code;

/**
 * Deletes tag.
 */
class GitDeleteTagOperation extends GitBranchOperation {

  @NotNull private final String myTagName;
  @NotNull private final VcsNotifier myNotifier;

  @NotNull private final Map<GitRepository, String> myDeletedTagTips = new HashMap<>();

  GitDeleteTagOperation(@NotNull Project project, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler,
                        @NotNull Collection<? extends GitRepository> repositories, @NotNull String tagName) {
    super(project, git, uiHandler, repositories);
    myTagName = tagName;
    myNotifier = VcsNotifier.getInstance(myProject);
  }

  @Override
  public void execute() {
    for (GitRepository repository: getRepositories()) {
      try {
        GitRevisionNumber revisionNumber = GitRevisionNumber.resolve(myProject, repository.getRoot(), GitTag.REFS_TAGS_PREFIX + myTagName);
        myDeletedTagTips.put(repository, revisionNumber.asString());
      }
      catch (VcsException e) {
        String title;
        if (!GitUtil.justOneGitRepository(myProject)) {
          title = GitBundle.message("delete.tag.operation.could.not.find.tag.in", repository.getPresentableUrl());
        }
        else {
          title = GitBundle.message("delete.tag.operation.could.not.find.tag", myTagName);
        }
        fatalError(title, "");
        return;
      }
    }

    while (hasMoreRepositories()) {
      GitRepository repository = next();
      GitCommandResult result = myGit.deleteTag(repository, myTagName);
      if (result.success()) {
        repository.getRepositoryFiles().refreshTagsFiles();
        markSuccessful(repository);
      }
      else {
        fatalError(GitBundle.message("delete.tag.operation.tag.was.not.deleted", myTagName), result);
        return;
      }
    }
    notifySuccess();
  }

  @Override
  protected void notifySuccess() {
    String message = GitBundle.message("delete.tag.operation.deleted.tag", myTagName);
    Notification notification = STANDARD_NOTIFICATION.createNotification("", message, NotificationType.INFORMATION);
    notification.setDisplayId(GitNotificationIdsHolder.TAG_DELETED);
    notification.addAction(NotificationAction.createSimple(GitBundle.messagePointer(
      "action.NotificationAction.GitDeleteTagOperation.text.restore"), () -> restoreInBackground(notification)));

    int remotes = 0;
    for (GitRepository repository: getRepositories()) {
      remotes += repository.getRemotes().size();
    }

    if (remotes > 0) {
      String text = GitBundle.message("delete.tag.operation.delete.on.remote", remotes);
      notification.addAction(NotificationAction.createSimple(text, () -> pushRemotesInBackground()));
    }
    myNotifier.notify(notification);
  }

  private void restoreInBackground(@NotNull Notification notification) {
    new Task.Backgroundable(myProject, GitBundle.message("delete.tag.operation.restoring.tag.process", myTagName)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        rollbackTagDeletion(notification);
      }
    }.queue();
  }

  @Override
  protected void rollback() {
    GitCompoundResult result = doRollback();
    if (result.totalSuccess()) {
      Notification notification = STANDARD_NOTIFICATION.createNotification(GitBundle.message("delete.tag.operation.rollback.successful"), GitBundle
          .message("delete.tag.operation.restored.tag", myTagName), NotificationType.INFORMATION);
      notification.setDisplayId(GitNotificationIdsHolder.TAG_RESTORED);
      myNotifier.notify(notification);
    }
    else {
      myNotifier.notifyError(TAG_DELETION_ROLLBACK_ERROR,
                             GitBundle.message("delete.tag.operation.error.during.rollback.of.tag.deletion"),
                             result.getErrorOutputWithReposIndication(),
                             true);
    }
  }

  private void rollbackTagDeletion(@NotNull Notification notification) {
    GitCompoundResult result = doRollback();
    if (result.totalSuccess()) {
      notification.expire();
    }
    else {
      myNotifier.notifyError(TAG_DELETION_ROLLBACK_ERROR,
                             GitBundle.message("delete.tag.operation.could.not.restore.tag", bold(code(myTagName))),
                             result.getErrorOutputWithReposIndication(),
                             true);
    }
  }

  @NotNull
  private GitCompoundResult doRollback() {
    GitCompoundResult result = new GitCompoundResult(myProject);
    for (GitRepository repository: getSuccessfulRepositories()) {
      GitCommandResult res = myGit.createNewTag(repository, myTagName, null, myDeletedTagTips.get(repository));
      result.append(repository, res);
      repository.getRepositoryFiles().refreshTagsFiles();
    }
    return result;
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    return new HtmlBuilder().append(GitBundle.message("delete.tag.operation.however.tag.deletion.has.succeeded.for.the.following",
                                                      getSkippedRepositories().size()))
      .br()
      .appendRaw(successfulRepositoriesJoined())
      .br()
      .append(GitBundle.message("delete.tag.operation.you.may.rollback.not.to.let.tags.diverge", myTagName))
      .toString();
  }

  @NotNull
  @Override
  protected String getOperationName() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  protected String getSuccessMessage() {
    throw new UnsupportedOperationException();
  }

  private void pushRemotesInBackground() {
    GitBrancher.getInstance(myProject).deleteRemoteTag(myTagName, ContainerUtil.map2Map(getRepositories(), it -> Pair.create(it, myDeletedTagTips.get(it))));
  }
}
