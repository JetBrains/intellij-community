// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.push.Pusher;
import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import git4idea.GitUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.update.GitUpdateInfoAsLog;
import git4idea.update.HashRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

import static java.util.Collections.emptyMap;

class GitPusher extends Pusher<GitRepository, GitPushSource, GitPushTarget> {

  private final @NotNull Project myProject;
  private final @NotNull GitVcsSettings mySettings;
  private final @NotNull GitPushSupport myPushSupport;

  GitPusher(@NotNull Project project, @NotNull GitVcsSettings settings, @NotNull GitPushSupport pushSupport) {
    myProject = project;
    mySettings = settings;
    myPushSupport = pushSupport;
  }

  @Override
  public void push(@NotNull Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                   @Nullable VcsPushOptionValue additionalOption,
                   boolean force) {
    push(pushSpecs, additionalOption, force, emptyMap());
  }

  @Override
  public void push(@NotNull Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                   @Nullable VcsPushOptionValue optionValue, boolean force,
                   @NotNull Map<String, VcsPushOptionValue> customParams) {
    expireExistingErrorsAndWarnings();
    GitPushTagMode pushTagMode;
    boolean skipHook;

    if (optionValue instanceof GitVcsPushOptionValue) {
      pushTagMode = ((GitVcsPushOptionValue)optionValue).getPushTagMode();
      skipHook = ((GitVcsPushOptionValue)optionValue).isSkipHook();
    }
    else {
      pushTagMode = null;
      skipHook = false;
    }

    mySettings.setPushTagMode(pushTagMode);

    GitPushOperation pushOperation = new GitPushOperation(myProject, myPushSupport, pushSpecs, pushTagMode, force, skipHook);
    pushAndNotify(myProject, pushOperation, customParams);
  }

  public static void pushAndNotify(@NotNull Project project,
                                   @NotNull GitPushOperation pushOperation,
                                   @NotNull Map<String, VcsPushOptionValue> customParams) {
    GitPushResult pushResult = pushOperation.execute();

    GitPushListener pushListener = project.getMessageBus().syncPublisher(GitPushListener.getTOPIC());

    for (Map.Entry<GitRepository, GitPushRepoResult> entry : pushResult.getResults().entrySet()) {
      pushListener.onCompleted(entry.getKey(), entry.getValue(), customParams);
    }

    Map<GitRepository, HashRange> updatedRanges = pushResult.getUpdatedRanges();
    GitUpdateInfoAsLog.NotificationData notificationData = !updatedRanges.isEmpty() ?
                                                           new GitUpdateInfoAsLog(project, updatedRanges).calculateDataAndCreateLogTab() :
                                                           null;

    ApplicationManager.getApplication().invokeLater(() -> {
      boolean multiRepoProject = GitUtil.getRepositoryManager(project).moreThanOneRoot();
      GitPushResultNotification.create(project, pushResult, pushOperation, multiRepoProject, notificationData, customParams)
        .notify(project);
    });
  }

  protected void expireExistingErrorsAndWarnings() {
    GitPushResultNotification[] existingNotifications =
      NotificationsManager.getNotificationsManager().getNotificationsOfType(GitPushResultNotification.class, myProject);
    for (GitPushResultNotification notification : existingNotifications) {
      if (notification.getType() != NotificationType.INFORMATION) {
        notification.expire();
      }
    }
  }
}
