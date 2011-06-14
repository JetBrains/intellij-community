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
package com.intellij.notification;

import com.intellij.notification.impl.NotificationModelListener;
import com.intellij.notification.impl.NotificationsConfiguration;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class LogModel {
  private final List<Notification> myNotifications = new ArrayList<Notification>();
  private Notification myStatusMessage;
  private final Project myProject;

  LogModel(@Nullable Project project) {
    myProject = project;

    NotificationsManagerImpl.getNotificationsManagerImpl().addListener(new NotificationModelListener() {
      @Override
      public void notificationsAdded(@NotNull Notification... notification) {
      }

      @Override
      public void notificationsRemoved(@NotNull Notification... notification) {
        List<Notification> list = Arrays.asList(notification);
        synchronized (myNotifications) {
          myNotifications.removeAll(list);
        }
        if (list.contains(myStatusMessage)) {
          setStatusMessage(null);
        }
      }

      @Override
      public void notificationsRead(@NotNull Notification... notification) {
      }
    }, project == null ? ApplicationManager.getApplication() : project);
  }

  void addNotification(Notification notification) {
    if (NotificationsConfiguration.getSettings(notification.getGroupId()).getDisplayType() != NotificationDisplayType.NONE) {
      synchronized (myNotifications) {
        myNotifications.add(notification);
      }
    }
    setStatusMessage(notification);
  }

  List<Notification> takeNotifications() {
    synchronized (myNotifications) {
      final ArrayList<Notification> result = new ArrayList<Notification>(myNotifications);
      myNotifications.clear();
      return result;
    }
  }

  void setStatusMessage(@Nullable Notification statusMessage) {
    synchronized (myNotifications) {
      myStatusMessage = statusMessage;
    }
    StatusBar.Info.set("", myProject, EventLog.LOG_REQUESTOR);
  }

  Notification getStatusMessage() {
    synchronized (myNotifications) {
      return myStatusMessage;
    }
  }

}
