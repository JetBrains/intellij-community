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
package git4idea.test;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import git4idea.Notificator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public class TestNotificator extends Notificator {

  public static final String TEST_NOTIFICATION_GROUP = "Test";
  private Notification myLastNotification;

  public TestNotificator(@NotNull Project project) {
    super(project);
  }

  public Notification getLastNotification() {
    return myLastNotification;
  }

  public void notify(@NotNull Notification notification) {
    myLastNotification = notification;
  }

  public void notify(@NotNull NotificationGroup notificationGroup, @NotNull String title, @NotNull String message, @NotNull NotificationType type) {
    notify(notificationGroup, title, message, type, null);
  }

  @Override
  public void notifyError(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    myLastNotification = createNotification(title, message, NotificationType.ERROR);
  }

  @Override
  public void notifySuccess(@NotNull String title, @NotNull String message) {
    myLastNotification = createNotification(title, message, NotificationType.INFORMATION);
  }

  @Override
  public void notifyWeakWarning(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    myLastNotification = createNotification(title, message, NotificationType.WARNING);
  }

  @Override
  public void notifyStrongWarning(@NotNull String title, @NotNull String content, @Nullable NotificationListener listener) {
    myLastNotification = createNotification(title, content, NotificationType.WARNING);
  }

  @NotNull
  private static Notification createNotification(@NotNull String title, @NotNull String message, NotificationType type) {
    return new Notification(TEST_NOTIFICATION_GROUP, title, message, type);
  }

  public void notify(@NotNull NotificationGroup notificationGroup, @NotNull String title, @NotNull String message,
                     @NotNull NotificationType type, @Nullable NotificationListener listener) {
    myLastNotification = createNotification(notificationGroup, title, message, type, listener);
  }

  public void cleanup() {
    myLastNotification = null;
  }
}
