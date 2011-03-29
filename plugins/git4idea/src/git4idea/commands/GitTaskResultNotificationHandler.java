/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.commands;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import git4idea.GitVcs;

/**
 * @author Kirill Likhodedov
 */
public class GitTaskResultNotificationHandler extends GitTaskResultHandlerAdapter {
  private final Project myProject;
  private final String mySuccessMessage;
  private final String myCancelMessage;
  private final String myErrorMessage;

  public GitTaskResultNotificationHandler(Project project, String successMessage, String cancelMessage, String errorMessage) {
    myProject = project;
    mySuccessMessage = successMessage;
    myCancelMessage = cancelMessage;
    myErrorMessage = errorMessage;
  }

  @Override protected void onSuccess() {
    Notifications.Bus.notify(new Notification(GitVcs.NOTIFICATION_GROUP_ID, "", mySuccessMessage, NotificationType.INFORMATION), myProject);
  }

  @Override protected void onCancel() {
    Notifications.Bus.notify(new Notification(GitVcs.NOTIFICATION_GROUP_ID, "", myCancelMessage, NotificationType.INFORMATION), myProject);
  }

  @Override protected void onFailure() {
    Notifications.Bus.notify(new Notification(GitVcs.NOTIFICATION_GROUP_ID, "", myErrorMessage, NotificationType.ERROR), myProject);
  }
}
