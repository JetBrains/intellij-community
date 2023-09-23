/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import java.util.Map;

class GitPusher extends Pusher<GitRepository, GitPushSource, GitPushTarget> {

  @NotNull private final Project myProject;
  @NotNull private final GitVcsSettings mySettings;
  @NotNull private final GitPushSupport myPushSupport;

  GitPusher(@NotNull Project project, @NotNull GitVcsSettings settings, @NotNull GitPushSupport pushSupport) {
    myProject = project;
    mySettings = settings;
    myPushSupport = pushSupport;
  }

  @Override
  public void push(@NotNull Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                   @Nullable VcsPushOptionValue optionValue, boolean force) {
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
    pushAndNotify(myProject, pushOperation);
  }

  public static void pushAndNotify(@NotNull Project project, @NotNull GitPushOperation pushOperation) {
    GitPushResult pushResult = pushOperation.execute();

    GitPushListener pushListener = project.getMessageBus().syncPublisher(GitPushListener.getTOPIC());
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : pushResult.getResults().entrySet()) {
      pushListener.onCompleted(entry.getKey(), entry.getValue());
    }

    Map<GitRepository, HashRange> updatedRanges = pushResult.getUpdatedRanges();
    GitUpdateInfoAsLog.NotificationData notificationData = !updatedRanges.isEmpty() ?
                                                           new GitUpdateInfoAsLog(project, updatedRanges).calculateDataAndCreateLogTab() :
                                                           null;

    ApplicationManager.getApplication().invokeLater(() -> {
      boolean multiRepoProject = GitUtil.getRepositoryManager(project).moreThanOneRoot();
      GitPushResultNotification.create(project, pushResult, pushOperation, multiRepoProject, notificationData).notify(project);
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
