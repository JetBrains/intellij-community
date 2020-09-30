// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NamedRunnable;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

/**
 * Shows a notification balloon over one of version control related tool windows: Changes View or Version Control View.
 * By default the notification is shown over the Changes View.
 * Use the special method or supply additional parameter to the constructor to show the balloon over the Version Control View.
 */
public class VcsBalloonProblemNotifier implements Runnable {
  public static final NotificationGroup
    NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("Common Version Control Messages", ChangesViewContentManager.TOOLWINDOW_ID);
  private final Project myProject;
  private final @NotificationContent String myMessage;
  private final MessageType myMessageType;
  private final NamedRunnable @Nullable [] myNotificationListener;

  public VcsBalloonProblemNotifier(@NotNull final Project project, @NotNull @NotificationContent String message, final MessageType messageType) {
    this(project, message, messageType, null);
  }

  public VcsBalloonProblemNotifier(@NotNull final Project project,
                                   @NotificationContent @NotNull final String message,
                                   final MessageType messageType,
                                   final NamedRunnable @Nullable [] notificationListener) {
    myProject = project;
    myMessage = message;
    myMessageType = messageType;
    myNotificationListener = notificationListener;
  }

  public static void showOverChangesView(@NotNull final Project project, @NotificationContent @NotNull final String message, final MessageType type,
                                         final NamedRunnable... notificationListener) {
    show(project, message, type, notificationListener);
  }

  public static void showOverVersionControlView(@NotNull final Project project, @NotificationContent @NotNull String message, final MessageType type) {
    show(project, message, type, null);
  }

  private static void show(final Project project, @NotificationContent final String message, final MessageType type,
                           final NamedRunnable @Nullable [] notificationListener) {
    final Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment()) return;
    final Runnable showErrorAction = () -> new VcsBalloonProblemNotifier(project, message, type, notificationListener).run();
    if (application.isDispatchThread()) {
      showErrorAction.run();
    }
    else {
      application.invokeLater(showErrorAction);
    }
  }

  @Override
  public void run() {
    final Notification notification;
    if (myNotificationListener != null && myNotificationListener.length > 0) {
      @Nls StringBuilder sb = new StringBuilder(myMessage);
      for (NamedRunnable runnable : myNotificationListener) {
        final String name = runnable.toString();
        sb.append("<br/><a href=\"").append(name).append("\">").append(name).append("</a>"); // NON-NLS
      }
      NotificationListener listener = (currentNotification, event) -> {
        if (HyperlinkEvent.EventType.ACTIVATED.equals(event.getEventType())) {
          if (myNotificationListener.length == 1) {
            myNotificationListener[0].run();
          }
          else {
            final String description = event.getDescription();
            if (description != null) {
              for (NamedRunnable runnable : myNotificationListener) {
                if (description.equals(runnable.toString())) {
                  runnable.run();
                  break;
                }
              }
            }
          }
          currentNotification.expire();
        }
      };
      notification = NOTIFICATION_GROUP.createNotification("", sb.toString(), myMessageType.toNotificationType(), listener);
    }
    else {
      notification = NOTIFICATION_GROUP.createNotification(myMessage, myMessageType);
    }
    notification.notify(myProject.isDefault() ? null : myProject);
  }
}
