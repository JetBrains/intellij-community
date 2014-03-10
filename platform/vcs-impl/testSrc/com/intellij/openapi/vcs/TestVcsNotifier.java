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
package com.intellij.openapi.vcs;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestVcsNotifier extends VcsNotifier {

  public static final String TEST_NOTIFICATION_GROUP = "Test";

  private Notification myLastNotification;

  public TestVcsNotifier(@NotNull Project project) {
    super(project);
  }

  public Notification getLastNotification() {
    return myLastNotification;
  }

  @Override
  @NotNull
  public Notification notify(@NotNull NotificationGroup notificationGroup, @NotNull String title, @NotNull String message,
                             @NotNull NotificationType type, @Nullable NotificationListener listener) {
    myLastNotification = new Notification(TEST_NOTIFICATION_GROUP, title, message, type, listener);
    return myLastNotification;
  }

  public void cleanup() {
    myLastNotification = null;
  }
}
