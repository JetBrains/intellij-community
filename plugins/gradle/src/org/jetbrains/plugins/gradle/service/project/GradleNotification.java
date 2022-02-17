// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.notification.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public class GradleNotification {
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Gradle Notification Group");

  /** @deprecated use {@code NOTIFICATION_GROUP.createNotification(...).notify(project)} */
  @Deprecated(forRemoval = true)
  public static @NotNull GradleNotification getInstance(@NotNull Project project) {
    return new GradleNotification(project);
  }

  private final Project myProject;

  private GradleNotification(Project project) {
    myProject = project;
  }

  /** @deprecated use {@code NOTIFICATION_GROUP.createNotification(...).notify(project)} */
  @Deprecated(forRemoval = true)
  public void showBalloon(@NotNull @NlsContexts.NotificationTitle final String title,
                          @NotNull  @NlsContexts.NotificationContent final String message,
                          @NotNull final NotificationType type,
                          @Nullable final NotificationListener listener) {
    Notification notification = NOTIFICATION_GROUP.createNotification(title, message, type);
    if (listener != null) notification.setListener(listener);
    notification.notify(myProject);
  }
}
