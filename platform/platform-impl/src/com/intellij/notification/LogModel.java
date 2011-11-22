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

import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class LogModel implements Disposable {
  private final List<Notification> myNotifications = new ArrayList<Notification>();
  private final Map<Notification, Long> myStamps = Collections.synchronizedMap(new WeakHashMap<Notification, Long>());
  private Notification myStatusMessage;
  private long myStatusTime;
  private final Project myProject;
  final Map<Notification, Runnable> removeHandlers = new THashMap<Notification, Runnable>();

  LogModel(@Nullable Project project, @NotNull Disposable parentDisposable) {
    myProject = project;
    Disposer.register(parentDisposable, this);
  }

  void addNotification(Notification notification) {
    long stamp = System.currentTimeMillis();
    NotificationDisplayType type = NotificationsConfigurationImpl.getSettings(notification.getGroupId()).getDisplayType();
    if (notification.isImportant() || (type != NotificationDisplayType.NONE && type != NotificationDisplayType.TOOL_WINDOW)) {
      synchronized (myNotifications) {
        myNotifications.add(notification);
      }
    }
    myStamps.put(notification, stamp);
    setStatusMessage(notification, stamp);
  }

  List<Notification> takeNotifications() {
    synchronized (myNotifications) {
      final ArrayList<Notification> result = getNotifications();
      myNotifications.clear();
      return result;
    }
  }

  void setStatusMessage(@Nullable Notification statusMessage, long stamp) {
    synchronized (myNotifications) {
      if (myStatusMessage == statusMessage) return;

      myStatusMessage = statusMessage;
      myStatusTime = stamp;
    }
    StatusBar.Info.set("", myProject, EventLog.LOG_REQUESTOR);
  }

  @Nullable 
  Pair<Notification, Long> getStatusMessage() {
    synchronized (myNotifications) {
      return myStatusMessage == null ? null : Pair.create(myStatusMessage, myStatusTime);
    }
  }

  public void logShown() {
    for (Notification notification : getNotifications()) {
      if (!notification.isImportant()) {
        removeNotification(notification);
      }
    }
    setStatusToImportant();
  }

  public ArrayList<Notification> getNotifications() {
    synchronized (myNotifications) {
      return new ArrayList<Notification>(myNotifications);
    }
  }

  @Nullable
  public Long getNotificationTime(Notification notification) {
    return myStamps.get(notification);
  }

  void removeNotification(Notification notification) {
    NotificationsManagerImpl.getNotificationsManagerImpl().remove(notification);
    synchronized (myNotifications) {
      myNotifications.remove(notification);
    }

    Runnable handler = removeHandlers.remove(notification);
    if (handler != null) {
      UIUtil.invokeLaterIfNeeded(handler);
    }

    Pair<Notification, Long> oldStatus = getStatusMessage();
    if (oldStatus != null && notification == oldStatus.first) {
      setStatusToImportant();
    }
  }

  private void setStatusToImportant() {
    ArrayList<Notification> notifications = getNotifications();
    Collections.reverse(notifications);
    Notification message = ContainerUtil.find(notifications, new Condition<Notification>() {
      @Override
      public boolean value(Notification notification) {
        return notification.isImportant();
      }
    });
    if (message == null) {
      setStatusMessage(message, 0);
    } else {
      Long notificationTime = getNotificationTime(message);
      assert notificationTime != null;
      setStatusMessage(message, notificationTime);
    }
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public void dispose() {
  }
}
