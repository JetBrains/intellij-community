// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.ViewUpdateInfoNotification;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.GitNotificationIdsHolder;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.update.GitUpdateInfoAsLog;
import git4idea.update.GitUpdateResult;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.intellij.openapi.util.text.HtmlChunk.raw;
import static com.intellij.openapi.vcs.update.ActionInfo.UPDATE;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Collections.singletonList;

final class GitPushResultNotification extends Notification {
  private static final Logger LOG = Logger.getInstance(GitPushResultNotification.class);

  private GitPushResultNotification(@NotNull String groupDisplayId,
                                    @NotNull @Nls String title,
                                    @NotNull @Nls String content,
                                    @NotNull NotificationType type) {
    super(groupDisplayId, "", emulateTitle(title, content), type);
    setDisplayId(GitNotificationIdsHolder.PUSH_RESULT);
  }

  @NotNull
  @RequiresEdt
  static GitPushResultNotification create(@NotNull Project project,
                                          @NotNull GitPushResult pushResult,
                                          @Nullable GitPushOperation pushOperation,
                                          boolean multiRepoProject,
                                          @Nullable GitUpdateInfoAsLog.NotificationData notificationData) {
    GroupedPushResult grouped = GroupedPushResult.group(pushResult.getResults());

    String title;
    NotificationType type;
    boolean singleRepoSuccess = false;
    if (!grouped.errors.isEmpty()) {
      if (!grouped.successful.isEmpty()) {
        title = GitBundle.message("push.notification.partially.failed.title");
      }
      else {
        title = GitBundle.message("push.notification.push.failed.title");
      }
      type = NotificationType.ERROR;
    }
    else if (!grouped.rejected.isEmpty() || !grouped.customRejected.isEmpty()) {
      if (!grouped.successful.isEmpty()) {
        title = GitBundle.message("push.notification.partially.rejected.title");
      }
      else {
        title = GitBundle.message("push.notification.rejected.title");
      }
      type = NotificationType.WARNING;
    }
    else {
      type = NotificationType.INFORMATION;
      if (!multiRepoProject) {
        singleRepoSuccess = true;
        GitPushRepoResult result = getFirstItem(grouped.successful.values());
        title = formRepoDescription(result);
      }
      else {
        title = GitBundle.message("push.notification.successful.title");
      }
    }

    String description;
    if (singleRepoSuccess) {
      if (notificationData != null) {
        int receivedCommitsCount = notificationData.getReceivedCommitsCount();
        description = GitBundle.message("push.notification.single.repo.success.description", receivedCommitsCount);
      }
      else { // nothing was updated
        description = "";
      }
    }
    else {
      description = formDescription(pushResult.getResults(), multiRepoProject);
    }

    NotificationGroup group = type == NotificationType.INFORMATION ?
                              VcsNotifier.STANDARD_NOTIFICATION :
                              VcsNotifier.IMPORTANT_ERROR_NOTIFICATION;

    GitPushResultNotification notification = new GitPushResultNotification(group.getDisplayId(), title, description, type);

    if (AbstractCommonUpdateAction.showsCustomNotification(singletonList(GitVcs.getInstance(project)))) {
      if (notificationData != null && notificationData.getReceivedCommitsCount() > 0) {
        Integer filteredCommitsCount = notificationData.getFilteredCommitsCount();
        String actionText;
        if (filteredCommitsCount == null || filteredCommitsCount == 0) {
          actionText = GitBundle.message("push.notification.view.received.commits.action", notificationData.getReceivedCommitsCount());
        }
        else {
          actionText = GitBundle.message("push.notification.view.filtered.commits.actions", filteredCommitsCount);
        }
        notification.addAction(NotificationAction.createSimple(actionText, notificationData.getViewCommitAction()));
      }
    }
    else {
      UpdatedFiles updatedFiles = pushResult.getUpdatedFiles();
      if (!updatedFiles.isEmpty()) {
        UpdateInfoTree tree = ProjectLevelVcsManagerEx.getInstanceEx(project).showUpdateProjectInfo(
          updatedFiles,
          GitBundle.message("push.notification.update.action"),
          UPDATE,
          false
        );
        if (tree != null) {
          tree.setBefore(pushResult.getBeforeUpdateLabel());
          tree.setAfter(pushResult.getAfterUpdateLabel());
          notification.addAction(new ViewUpdateInfoNotification(
            project,
            tree,
            GitBundle.message("push.notification.view.files.action"),
            notification
          ));
        }
      }
    }

    List<GitRepository> staleInfoRejected = EntryStream.of(pushResult.getResults())
      .filterValues(result -> result.getType() == GitPushRepoResult.Type.REJECTED_STALE_INFO)
      .keys().toList();
    if (!staleInfoRejected.isEmpty()) {
      notification.setContextHelpAction(new AnAction(
        GitBundle.message("push.notification.force.with.lease.help"),
        new HtmlBuilder()
          .append(GitBundle.message("push.notification.force.with.lease.help.description.first")).br()
          .append(GitBundle.message("push.notification.force.with.lease.help.description.second"))
          .toString(),
        null
      ) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
        }
      });
      if (pushOperation != null) {
        notification.addAction(new ForcePushNotificationAction(project, pushOperation, staleInfoRejected));
      }
    }

    if (!grouped.errors.isEmpty() ||
        !grouped.rejected.isEmpty() ||
        !grouped.customRejected.isEmpty()) {
      VcsNotifier.addShowDetailsAction(project, notification);
    }

    return notification;
  }

  @NlsContexts.NotificationContent
  @NotNull
  static String emulateTitle(@NotNull @Nls String title, @NotNull @Nls String content) {
    return new HtmlBuilder()
      .append(raw(title).bold()).br()
      .appendRaw(content)
      .toString();
  }

  private static @Nls String formDescription(@NotNull Map<GitRepository, GitPushRepoResult> results, final boolean multiRepoProject) {
    List<Map.Entry<GitRepository, GitPushRepoResult>> entries = ContainerUtil.sorted(results.entrySet(), (o1, o2) -> {
      // successful first
      int compareResultTypes = GitPushRepoResult.TYPE_COMPARATOR.compare(o1.getValue().getType(), o2.getValue().getType());
      if (compareResultTypes != 0) {
        return compareResultTypes;
      }
      return DvcsUtil.REPOSITORY_COMPARATOR.compare(o1.getKey(), o2.getKey());
    });

    return new HtmlBuilder().appendWithSeparators(HtmlChunk.br(), map(entries, entry -> {
      GitRepository repository = entry.getKey();
      GitPushRepoResult result = entry.getValue();

      String description;
      if (multiRepoProject) {
        description = DvcsUtil.getShortRepositoryName(repository) + ": " + formRepoDescription(result);
      }
      else {
        description = formRepoDescription(result);
      }
      return raw(description);
    })).toString();
  }

  private static @Nls String selectBundleMessageWithTags(
    List<@NlsSafe String> pushedTags,
    @Nls Supplier<@Nls String> withoutTagsMessage,
    @Nls Supplier<@Nls String> singleTagMessage,
    @Nls Supplier<@Nls String> manyTagsMessage
  ) {
    if (pushedTags.isEmpty()) {
      return withoutTagsMessage.get();
    }
    else if (pushedTags.size() == 1) {
      return singleTagMessage.get();
    }
    else {
      return manyTagsMessage.get();
    }
  }

  private static @NlsSafe String tagName(List<@NlsSafe String> pushedTags) {
    return GitBranchUtil.stripRefsPrefix(pushedTags.get(0));
  }

  private static @NlsContexts.NotificationContent String formRepoDescription(@NotNull GitPushRepoResult result) {
    String sourceBranch = GitBranchUtil.stripRefsPrefix(result.getSourceBranch());
    String targetBranch = GitBranchUtil.stripRefsPrefix(result.getTargetBranch());
    @NotNull List<String> pushedTags = result.getPushedTags();
    @NotNull String remoteName = result.getTargetRemote();

    @NlsContexts.NotificationContent String description;
    switch (result.getType()) {
      case SUCCESS:
        int commitNum = result.getNumberOfPushedCommits();
        description = selectBundleMessageWithTags(
          pushedTags,
          () -> GitBundle.message("push.notification.description.pushed", commitNum, targetBranch),
          () -> GitBundle.message("push.notification.description.pushed.with.single.tag", commitNum, targetBranch, tagName(pushedTags), remoteName),
          () -> GitBundle.message("push.notification.description.pushed.with.many.tags", commitNum, targetBranch, pushedTags.size(), remoteName)
        );
        break;
      case NEW_BRANCH:
        description = selectBundleMessageWithTags(
          pushedTags,
          () -> GitBundle.message("push.notification.description.new.branch", sourceBranch, targetBranch),
          () -> GitBundle.message("push.notification.description.new.branch.with.single.tag", sourceBranch, targetBranch, tagName(pushedTags), remoteName),
          () -> GitBundle.message("push.notification.description.new.branch.with.many.tags", sourceBranch, targetBranch, pushedTags.size(), remoteName)
        );
        break;
      case UP_TO_DATE:
        description = selectBundleMessageWithTags(
          pushedTags,
          () -> GitBundle.message("push.notification.description.up.to.date"),
          () -> GitBundle.message("push.notification.description.pushed.single.tag", tagName(pushedTags), remoteName),
          () -> GitBundle.message("push.notification.description.pushed.many.tags", pushedTags.size(), remoteName)
        );
        break;
      case FORCED:
        description = GitBundle.message("push.notification.description.force.pushed", sourceBranch, targetBranch);
        break;
      case REJECTED_NO_FF:
        GitUpdateResult updateResult = result.getUpdateResult();
        if (updateResult == null || updateResult == GitUpdateResult.SUCCESS || updateResult == GitUpdateResult.NOTHING_TO_UPDATE) {
          description = GitBundle.message("push.notification.description.rejected", targetBranch);
        }
        else if (updateResult == GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS) {
          description = GitBundle.message("push.notification.description.rejected.and.conflicts");
        }
        else if (updateResult == GitUpdateResult.INCOMPLETE) {
          description = GitBundle.message("push.notification.description.rejected.and.incomplete");
        }
        else if (updateResult == GitUpdateResult.CANCEL) {
          description = GitBundle.message("push.notification.description.rejected.and.cancelled");
        }
        else {
          description = GitBundle.message("push.notification.description.rejected.and.failed");
        }
        break;
      case REJECTED_STALE_INFO:
        description = GitBundle.message("push.notification.description.push.with.lease.rejected", sourceBranch, targetBranch);
        break;
      case REJECTED_OTHER:
        description = GitBundle.message("push.notification.description.rejected.by.remote", sourceBranch, targetBranch);
        break;
      case ERROR:
        description = XmlStringUtil.escapeString(result.getError());
        break;
      default:
        LOG.error("Unexpected push result: " + result);
        description = "";
        break;
    }
    return description;
  }

  private static final class ForcePushNotificationAction extends NotificationAction {
    @NotNull private final Project myProject;
    @NotNull private final GitPushOperation myOperation;
    @NotNull private final List<GitRepository> myRepositories;

    private ForcePushNotificationAction(@NotNull Project project,
                                        @NotNull GitPushOperation pushOperation,
                                        @NotNull List<GitRepository> repositories) {
      super(GitBundle.message("push.notification.force.push.anyway.action"));
      myProject = project;
      myOperation = pushOperation;
      myRepositories = repositories;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      notification.expire();

      Project project = myProject;
      new Task.Backgroundable(project, GitBundle.message("push.notification.force.push.progress.title.pushing"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          GitPushOperation forcePushOperation = myOperation.deriveForceWithoutLease(myRepositories);
          GitPusher.pushAndNotify(project, forcePushOperation);
        }
      }.queue();
    }
  }
}
