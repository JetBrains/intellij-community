// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitNotificationIdsHolder;
import git4idea.GitTag;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.i18n.GitBundle;
import git4idea.push.GitPushParams.ForceWithLease;
import git4idea.push.GitPushParamsImpl;
import git4idea.push.GitPushParamsImpl.ForceWithLeaseReference;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION;
import static git4idea.GitNotificationIdsHolder.TAG_REMOTE_DELETION_ERROR;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

class GitDeleteRemoteTagOperation extends GitBranchOperation {
  @NotNull private final Map<GitRepository, String> myRepositories;
  private final String myTagName;

  GitDeleteRemoteTagOperation(@NotNull Project project, @NotNull Git git,
                                     @NotNull GitBranchUiHandler handler,
                                     @NotNull Map<GitRepository, String> repositories,
                                     @NotNull String name) {
    super(project, git, handler, repositories.keySet());
    myRepositories = repositories;
    myTagName = name;
  }

  @Override
  protected void execute() {
    String tagFullName = GitTag.REFS_TAGS_PREFIX + myTagName;

    int successRemotes = 0;
    int failureRemotes = 0;

    GitCompoundResult result = new GitCompoundResult(myProject);
    Collection<GitRepository> repositories = getRepositories();
    for (GitRepository repository: repositories) {
      String expectedCommit = myRepositories.get(repository);
      List<ForceWithLease> forceWithLease = expectedCommit != null
                                            ? singletonList(new ForceWithLeaseReference(tagFullName, expectedCommit))
                                            : emptyList();

      for (GitRemote remote: repository.getRemotes()) {
        GitCommandResult lsRemoteResult =
          myGit.lsRemoteRefs(myProject, repository.getRoot(), remote, singletonList(tagFullName), "--tags"); //NON-NLS
        if (!lsRemoteResult.success()) {
          result.append(repository, lsRemoteResult);
          continue;
        }

        if (hasTagOnRemote(tagFullName, lsRemoteResult.getOutput())) {
          GitCommandResult pushResult = myGit.push(repository, new GitPushParamsImpl(remote, ":" + tagFullName,
                                                                                     false, false, false, null, forceWithLease));
          result.append(repository, pushResult);

          if (pushResult.success()) {
            successRemotes++;
          }
          else {
            failureRemotes++;
          }
        }
      }

      repository.update();
    }

    int remotesCount = ContainerUtil.exists(repositories, it -> it.getRemotes().size() > 1) ? 1 : 2;

    if (successRemotes > 0) {
      String message = GitBundle.message("delete.remote.tag.operation.deleted.tag.on.remotes",
                                         remotesCount,
                                         myTagName);
      notifySuccessWithEmptyTitle(message);
    }
    else if (successRemotes == 0 && failureRemotes == 0) {
      String message = GitBundle.message("delete.remote.tag.operation.tag.does.not.exist.on.remotes",
                                         remotesCount,
                                         myTagName);
      notifySuccessWithEmptyTitle(message);
    }

    if (!result.totalSuccess()) {
      String title = GitBundle.message("delete.remote.tag.operation.failed.to.delete.tag.on.remotes",
                                       myTagName,
                                       remotesCount);
      VcsNotifier.getInstance(myProject)
        .notifyError(TAG_REMOTE_DELETION_ERROR, title, result.getErrorOutputWithReposIndication(), true);
    }
  }

  private static boolean hasTagOnRemote(@NotNull String tagFullName, @NotNull List<String> lsRemoteOutput) {
    return ContainerUtil.exists(lsRemoteOutput, line -> {
      if (StringUtil.isEmptyOrSpaces(line)) return false;
      List<String> split = StringUtil.split(line, "\t");
      if (split.size() != 2) return false;
      return tagFullName.equals(split.get(1));
    });
  }

  private void notifySuccessWithEmptyTitle(@NotNull @NlsContexts.NotificationContent String message) {
    Notification notification = STANDARD_NOTIFICATION.createNotification(message, NotificationType.INFORMATION);
    notification.setDisplayId(GitNotificationIdsHolder.TAG_REMOTE_DELETION_SUCCESS);
    VcsNotifier.getInstance(myProject).notify(notification);
  }

  @Override
  protected void rollback() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected String getSuccessMessage() {
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
}
