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

import com.intellij.notification.impl.NotificationsConfiguration;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
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
      final ArrayList<Notification> result = getNotifications();
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

  public void logShown() {
    for (Notification notification : getNotifications()) {
      if (!notification.isImportant()) {
        removeNotification(notification);
      }
    }
  }

  public ArrayList<Notification> getNotifications() {
    synchronized (myNotifications) {
      return new ArrayList<Notification>(myNotifications);
    }
  }

  void removeNotification(Notification notification) {
    NotificationsManagerImpl.getNotificationsManagerImpl().remove(notification);
    synchronized (myNotifications) {
      myNotifications.remove(notification);
    }
    if (notification == getStatusMessage() && notification.isImportant()) {
      ArrayList<Notification> notifications = getNotifications();
      Collections.reverse(notifications);
      setStatusMessage(ContainerUtil.find(notifications, new Condition<Notification>() {
        @Override
        public boolean value(Notification notification) {
          return notification.isImportant();
        }
      }));
    }
  }
}
