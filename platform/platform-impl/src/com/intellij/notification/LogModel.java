// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public final class LogModel  {
  public static final Topic<EventLogListener> LOG_MODEL_CHANGED = Topic.create("LOG_MODEL_CHANGED", EventLogListener.class, Topic.BroadcastDirection.NONE);

  private final List<Notification> myNotifications = new ArrayList<>();
  private final Map<Notification, @NlsContexts.StatusBarText String> myStatuses = CollectionFactory.createConcurrentWeakIdentityMap();
  private Trinity<Notification, @NlsContexts.StatusBarText String, Long> myStatusMessage;
  private final Project myProject;
  final Map<Notification, Runnable> removeHandlers = new HashMap<>();

  LogModel(@Nullable Project project) {
    myProject = project;
  }

  void addNotification(Notification notification) {
    long stamp = System.currentTimeMillis();
    NotificationDisplayType type = NotificationsConfigurationImpl.getSettings(notification.getGroupId()).getDisplayType();
    myStatuses.put(notification, EventLog.formatForLog(notification, "").status);
    if (notification.isImportant() || (type != NotificationDisplayType.NONE && type != NotificationDisplayType.TOOL_WINDOW)) {
      synchronized (myNotifications) {
        myNotifications.add(notification);
      }
    }
    setStatusMessage(notification, stamp);
    fireModelChanged();
  }

  public static void fireModelChanged() {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(LOG_MODEL_CHANGED).modelChanged();
  }

  List<Notification> takeNotifications() {
    final ArrayList<Notification> result;
    synchronized (myNotifications) {
      result = getNotifications();
      myNotifications.clear();
    }
    if (!result.isEmpty()) {
      fireModelChanged();
    }
    return result;
  }

  public void setStatusMessage(@Nullable Notification notification) {
    if (notification == null) {
      setStatusMessage(null, 0);
    }
    else {
      myStatuses.put(notification, EventLog.formatForLog(notification, "").status);
      setStatusMessage(notification, notification.getTimestamp());
    }
  }

  void setStatusMessage(@Nullable Notification statusMessage, long stamp) {
    synchronized (myNotifications) {
      if (myStatusMessage != null && myStatusMessage.first == statusMessage) return;
      if (myStatusMessage == null && statusMessage == null) return;

      myStatusMessage = statusMessage == null ? null : Trinity.create(statusMessage,
                                                                      Objects.requireNonNull(myStatuses.get(statusMessage)), stamp);
    }
    StatusBar.Info.set("", myProject, EventLog.LOG_REQUESTOR);
  }

  @Nullable
  Trinity<Notification, @NlsContexts.StatusBarText String, Long> getStatusMessage() {
    synchronized (myNotifications) {
      return myStatusMessage;
    }
  }

  void logShown() {
    for (Notification notification : getNotifications()) {
      if (!notification.isImportant()) {
        removeNotification(notification);
      }
    }
    setStatusToImportant();
  }

  public void projectDispose(@Nullable LogModel applicationModel) {
    if (applicationModel != null) {
      synchronized (applicationModel.myNotifications) {
        applicationModel.myNotifications.removeAll(myNotifications);
        for (Notification notification : myNotifications) {
          applicationModel.myStatuses.remove(notification);
          applicationModel.removeHandlers.remove(notification);
        }
      }
    }
    synchronized (myNotifications) {
      myNotifications.clear();
      myStatuses.clear();
      removeHandlers.clear();
      myStatusMessage = null;
    }
  }

  public @NotNull ArrayList<Notification> getNotifications() {
    synchronized (myNotifications) {
      return new ArrayList<>(myNotifications);
    }
  }
  public void removeNotification(Notification notification) {
    synchronized (myNotifications) {
      myNotifications.remove(notification);
    }

    Runnable handler = removeHandlers.remove(notification);
    if (handler != null) {
      UIUtil.invokeLaterIfNeeded(handler);
    }

    Trinity<Notification, String, Long> oldStatus = getStatusMessage();
    if (oldStatus != null && notification == oldStatus.first) {
      setStatusToImportant();
    }
    fireModelChanged();
  }

  private void setStatusToImportant() {
    ArrayList<Notification> notifications = getNotifications();
    Collections.reverse(notifications);
    Notification message = ContainerUtil.find(notifications, Notification::isImportant);
    if (message == null) {
      setStatusMessage(null, 0);
    }
    else {
      setStatusMessage(message, message.getTimestamp());
    }
  }

  public Project getProject() {
    return myProject;
  }
}
