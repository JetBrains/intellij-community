// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.ViewUpdateInfoNotification;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import git4idea.update.GitUpdateInfoAsLog;
import git4idea.update.GitUpdateResult;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static com.intellij.openapi.vcs.update.ActionInfo.UPDATE;
import static java.util.Collections.singletonList;

final class GitPushResultNotification extends Notification {

  public static final String VIEW_FILES_UPDATED_DURING_THE_PUSH = "View files updated during the push";

  public static final String UPDATE_WITH_RESOLVED_CONFLICTS = "push has been cancelled, because there were conflicts during update.<br/>" +
                                                              "Check that conflicts were resolved correctly, and invoke push again.";
  public static final String INCOMPLETE_UPDATE = "push has been cancelled, because not all conflicts were resolved during update.<br/>" +
                                                 "Resolve the conflicts and invoke push again.";
  public static final String UPDATE_WITH_ERRORS = "push was rejected, and update failed with error.";
  public static final String UPDATE_CANCELLED = "push was rejected, and update was cancelled.";

  private static final Logger LOG = Logger.getInstance(GitPushResultNotification.class);

  private GitPushResultNotification(@NotNull String groupDisplayId,
                                    @NotNull @Nls String title,
                                    @NotNull @Nls String content,
                                    @NotNull NotificationType type) {
    super(groupDisplayId, "", emulateTitle(title, content), type);
  }

  @NotNull
  @CalledInAwt
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
        title = "Push partially failed";
      }
      else {
        title = "Push failed";
      }
      type = NotificationType.ERROR;
    }
    else if (!grouped.rejected.isEmpty() || !grouped.customRejected.isEmpty()) {
      if (!grouped.successful.isEmpty()) {
        title = "Push partially rejected";
      }
      else {
        title = "Push rejected";
      }
      type = NotificationType.WARNING;
    }
    else {
      type = NotificationType.INFORMATION;
      if (!multiRepoProject) {
        singleRepoSuccess = true;
        GitPushRepoResult result = grouped.successful.values().iterator().next();
        title = StringUtil.capitalize(formRepoDescription(result));
      }
      else {
        title = "Push successful";
      }
    }

    String description;
    if (singleRepoSuccess) {
      if (notificationData != null) {
        int receivedCommitsCount = notificationData.getReceivedCommitsCount();
        description = String.format("%d %s received during the push", receivedCommitsCount, commits(receivedCommitsCount));
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
          actionText = "View received " + commits(notificationData.getReceivedCommitsCount());
        }
        else {
          actionText = String.format("View %d %s matching the filter", filteredCommitsCount, commits(filteredCommitsCount));
        }
        notification.addAction(NotificationAction.createSimple(actionText, notificationData.getViewCommitAction()));
      }
    }
    else {
      UpdatedFiles updatedFiles = pushResult.getUpdatedFiles();
      if (!updatedFiles.isEmpty()) {
        UpdateInfoTree tree = ProjectLevelVcsManagerEx.getInstanceEx(project).showUpdateProjectInfo(updatedFiles, "Update", UPDATE, false);
        if (tree != null) {
          tree.setBefore(pushResult.getBeforeUpdateLabel());
          tree.setAfter(pushResult.getAfterUpdateLabel());
          notification.addAction(new ViewUpdateInfoNotification(project, tree, VIEW_FILES_UPDATED_DURING_THE_PUSH, notification));
        }
      }
    }

    List<GitRepository> staleInfoRejected = EntryStream.of(pushResult.getResults())
      .filterValues(result -> result.getType() == GitPushRepoResult.Type.REJECTED_STALE_INFO)
      .keys().toList();
    if (!staleInfoRejected.isEmpty()) {
      notification.setContextHelpAction(new AnAction(
        "What is force-with-lease?",
        "Force-with-lease push prevents overriding remote changes that are unknown to local repository.<br>" +
        "Fetch latest changes to verify that they can be safely discarded and repeat push operation.", null) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
        }
      });
      if (pushOperation != null) {
        notification.addAction(new ForcePushNotificationAction(project, pushOperation, staleInfoRejected));
      }
    }

    if (Registry.is("vcs.showConsole")
        && !grouped.errors.isEmpty()
        || !grouped.rejected.isEmpty()
        || !grouped.customRejected.isEmpty()) {
      notification.addAction(NotificationAction.createSimple(
        VcsBundle.message("notification.showDetailsInConsole"),
        () -> {
          ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
          vcsManager.showConsole(vcsManager::scrollConsoleToTheEnd);
        }));
    }

    return notification;
  }

  @NotNull
  static String emulateTitle(@NotNull @Nls String title, @NotNull @Nls String content) {
    return "<b>" + title + "</b><br/>" + content;
  }

  @NotNull
  private static String commits(int commitsNumber) {
    return pluralize("commit", commitsNumber);
  }

  private static String formDescription(@NotNull Map<GitRepository, GitPushRepoResult> results, final boolean multiRepoProject) {
    List<Map.Entry<GitRepository, GitPushRepoResult>> entries = ContainerUtil.sorted(results.entrySet(),
      (o1, o2) -> {
       // successful first
       int compareResultTypes = GitPushRepoResult.TYPE_COMPARATOR.compare(o1.getValue().getType(), o2.getValue().getType());
       if (compareResultTypes != 0) {
         return compareResultTypes;
       }
       return DvcsUtil.REPOSITORY_COMPARATOR.compare(o1.getKey(), o2.getKey());
      });

    return StringUtil.join(entries, entry -> {
      GitRepository repository = entry.getKey();
      GitPushRepoResult result = entry.getValue();

      String description = formRepoDescription(result);
      if (!multiRepoProject) {
        description = StringUtil.capitalize(description);
      }
      else {
        description = DvcsUtil.getShortRepositoryName(repository) + ": " + description;
      }
      return description;
    }, "<br/>");
  }

  private static @Nls String formRepoDescription(@NotNull GitPushRepoResult result) {
    @Nls String description;
    String sourceBranch = GitBranchUtil.stripRefsPrefix(result.getSourceBranch());
    String targetBranch = GitBranchUtil.stripRefsPrefix(result.getTargetBranch());
    String tagDescription = formTagDescription(result.getPushedTags(), result.getTargetRemote());
    switch (result.getType()) {
      case SUCCESS:
        int commitNum = result.getNumberOfPushedCommits();
        String commits = pluralize("commit", commitNum);
        description = String.format("pushed %d %s to %s", commitNum, commits, targetBranch);
        if (tagDescription != null) {
          description += ", and " + tagDescription;
        }
        break;
      case NEW_BRANCH:
        description = String.format("pushed %s to new branch %s", sourceBranch, targetBranch);
        if (tagDescription != null) {
          description += ", and " + tagDescription;
        }
        break;
      case UP_TO_DATE:
        if (tagDescription != null) {
          description = "pushed " + tagDescription;
        }
        else {
          description = "everything is up-to-date";
        }
        break;
      case FORCED:
        description = String.format("force pushed %s to %s", sourceBranch, targetBranch);
        break;
      case REJECTED_NO_FF:
        description = formDescriptionBasedOnUpdateResult(result.getUpdateResult(), targetBranch);
        break;
      case REJECTED_STALE_INFO:
        description = String.format("force-with-lease push %s to %s was rejected", sourceBranch, targetBranch);
        break;
      case REJECTED_OTHER:
        description = String.format("push %s to %s was rejected by remote", sourceBranch, targetBranch);
        break;
      case ERROR:
        description = result.getError();
        break;
      default:
        LOG.error("Unexpected push result: " + result);
        description = "";
        break;
    }
    return description;
  }

  @Nullable
  private static @Nls String formTagDescription(@NotNull List<String> pushedTags, @NotNull String remoteName) {
    if (pushedTags.isEmpty()) {
      return null;
    }
    if (pushedTags.size() == 1) {
      return "tag " + GitBranchUtil.stripRefsPrefix(pushedTags.get(0)) + " to " + remoteName;
    }
    return pushedTags.size() + " tags to " + remoteName;
  }

  private static @Nls String formDescriptionBasedOnUpdateResult(GitUpdateResult updateResult, String targetBranch) {
    if (updateResult == null || updateResult == GitUpdateResult.SUCCESS || updateResult == GitUpdateResult.NOTHING_TO_UPDATE) {
      return String.format("push to %s was rejected", targetBranch);
    }
    else if (updateResult == GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS) {
      return UPDATE_WITH_RESOLVED_CONFLICTS;
    }
    else if (updateResult == GitUpdateResult.INCOMPLETE) {
      return INCOMPLETE_UPDATE;
    }
    else if (updateResult == GitUpdateResult.CANCEL) {
      return UPDATE_CANCELLED;
    }
    else {
      return UPDATE_WITH_ERRORS;
    }
  }

  private static final class ForcePushNotificationAction extends NotificationAction {
    @NotNull private final Project myProject;
    @NotNull private final GitPushOperation myOperation;
    @NotNull private final List<GitRepository> myRepositories;

    private ForcePushNotificationAction(@NotNull Project project,
                                        @NotNull GitPushOperation pushOperation,
                                        @NotNull List<GitRepository> repositories) {
      super("Force Push Anyway");
      myProject = project;
      myOperation = pushOperation;
      myRepositories = repositories;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      notification.expire();

      Project project = myProject;
      new Task.Backgroundable(project, "Pushing...", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          GitPushOperation forcePushOperation = myOperation.deriveForceWithoutLease(myRepositories);
          GitPusher.pushAndNotify(project, forcePushOperation);
        }
      }.queue();
    }
  }
}
