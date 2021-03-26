// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public class GradleNotification {
  public static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Gradle Notification Group",
                                                                                    NotificationDisplayType.STICKY_BALLOON,
                                                                                    true);

  @NotNull private final Project myProject;

  @NotNull
  public static GradleNotification getInstance(@NotNull Project project) {
    return project.getService(GradleNotification.class);
  }

  public GradleNotification(@NotNull Project project) {
    myProject = project;
  }

  public void showBalloon(@NotNull @NlsContexts.NotificationTitle final String title,
                          @NotNull  @NlsContexts.NotificationContent final String message,
                          @NotNull final NotificationType type,
                          @Nullable final NotificationListener listener) {
    NOTIFICATION_GROUP.createNotification(title, message, type, listener).notify(myProject);
  }
}

