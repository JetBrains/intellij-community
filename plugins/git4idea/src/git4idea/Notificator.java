/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public class Notificator {

  private final @NotNull Project myProject;

  public static Notificator getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, Notificator.class);
  }

  public Notificator(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static Notification createNotification(@NotNull NotificationGroup notificationGroup,
                                                @NotNull String title, @NotNull String message, @NotNull NotificationType type,
                                                @Nullable NotificationListener listener) {
    // title can be empty; description can't be neither null, nor empty
    if (StringUtil.isEmptyOrSpaces(message)) {
      message = title;
      title = "";
    }
    // if both title and description were empty, then it is a problem in the calling code => Notifications engine assertion will notify.
    return notificationGroup.createNotification(title, message, type, listener);
  }

  public void notify(@NotNull Notification notification) {
    notification.notify(myProject);
  }

  public void notify(@NotNull NotificationGroup notificationGroup, @NotNull String title, @NotNull String message,
                     @NotNull NotificationType type, @Nullable NotificationListener listener) {
    createNotification(notificationGroup, title, message, type, listener).notify(myProject);
  }

  public void notify(@NotNull NotificationGroup notificationGroup, @NotNull String title, @NotNull String message,
                     @NotNull NotificationType type) {
    notify(notificationGroup, title, message, type, null);
  }

  public void notifyError(@NotNull String title, @NotNull String message) {
    notifyError(title, message, null);
  }

  public void notifyError(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.ERROR, listener);
  }

  public void notifySuccess(@NotNull String title, @NotNull String message) {
    notify(GitVcs.NOTIFICATION_GROUP_ID, title, message, NotificationType.INFORMATION,  null);
  }

  public void notifyWeakWarning(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    notify(GitVcs.MINOR_NOTIFICATION, title, message, NotificationType.WARNING, listener);
  }

  public void notifyStrongWarning(@NotNull String title, @NotNull String content, @Nullable NotificationListener listener) {
    notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, title, content, NotificationType.WARNING, listener);
  }

}
