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
package com.intellij.openapi.vcs.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.NamedRunnable;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

/**
 * Shows a notification balloon over one of version control related tool windows: Changes View or Version Control View.
 * By default the notification is shown over the Changes View.
 * Use the special method or supply additional parameter to the constructor to show the balloon over the Version Control View.
 */
public class VcsBalloonProblemNotifier implements Runnable {
  public static final NotificationGroup
    NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("Common Version Control Messages", ChangesViewContentManager.TOOLWINDOW_ID, true);
  private final Project myProject;
  private final String myMessage;
  private final MessageType myMessageType;
  private final boolean myShowOverChangesView;
  @Nullable private final NamedRunnable[] myNotificationListener;

  public VcsBalloonProblemNotifier(@NotNull final Project project, @NotNull final String message, final MessageType messageType) {
    this(project, message, messageType, true, null);
  }

  public VcsBalloonProblemNotifier(@NotNull final Project project, @NotNull final String message, final MessageType messageType, boolean showOverChangesView,
                                   @Nullable final NamedRunnable[] notificationListener) {
    myProject = project;
    myMessage = message;
    myMessageType = messageType;
    myShowOverChangesView = showOverChangesView;
    myNotificationListener = notificationListener;
  }

  public static void showOverChangesView(@NotNull final Project project, @NotNull final String message, final MessageType type,
                                         final NamedRunnable... notificationListener) {
    show(project, message, type, true, notificationListener);
  }

  public static void showOverVersionControlView(@NotNull final Project project, @NotNull final String message, final MessageType type) {
    show(project, message, type, false, null);
  }

  private static void show(final Project project, final String message, final MessageType type, final boolean showOverChangesView,
                           @Nullable final NamedRunnable[] notificationListener) {
    final Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment()) return;
    final Runnable showErrorAction = new Runnable() {
      public void run() {
        new VcsBalloonProblemNotifier(project, message, type, showOverChangesView, notificationListener).run();
      }
    };
    if (application.isDispatchThread()) {
      showErrorAction.run();
    }
    else {
      application.invokeLater(showErrorAction);
    }
  }

  public void run() {
    final Notification notification;
    if (myNotificationListener != null && myNotificationListener.length > 0) {
      final NotificationType type = myMessageType.toNotificationType();
      final StringBuilder sb = new StringBuilder(myMessage);
      for (NamedRunnable runnable : myNotificationListener) {
        final String name = runnable.toString();
        sb.append("<br/><a href=\"").append(name).append("\">").append(name).append("</a>");
      }
      notification = NOTIFICATION_GROUP.createNotification(type.name(), sb.toString(), myMessageType.toNotificationType(),
        new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          if (HyperlinkEvent.EventType.ACTIVATED.equals(event.getEventType())) {
            if (myNotificationListener.length == 1) {
              myNotificationListener[0].run();
            } else {
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
            notification.expire();
          }
        }
      });
    } else {
      notification = NOTIFICATION_GROUP.createNotification(myMessage, myMessageType);
    }
    notification.notify(myProject.isDefault() ? null : myProject);
  }

  public static void showBalloonForActiveComponent(@NotNull final String message, final MessageType type) {
    Runnable runnable = new Runnable() {
      public void run() {
        Window[] windows = Window.getWindows();
        Window targetWindow = null;
        for (Window each : windows) {
          if (each.isActive()) {
            targetWindow = each;
            break;
          }
        }

        if (targetWindow == null) {
          targetWindow = JOptionPane.getRootFrame();
        }

        if (targetWindow == null) {
          final IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
          if (frame == null) {
            final Project[] projects = ProjectManager.getInstance().getOpenProjects();
            showOverChangesView(projects == null || projects.length == 0 ? ProjectManager.getInstance().getDefaultProject() : projects[0],
                                message, type);
            return;
          }
          PopupUtil.showBalloonForComponent(frame.getComponent(), message, type, true);
        } else {
          PopupUtil.showBalloonForComponent(targetWindow, message, type, true);
        }
      }
    };
    UIUtil.invokeLaterIfNeeded(runnable);
  }
}
