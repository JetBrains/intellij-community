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

import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

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

  public VcsBalloonProblemNotifier(@NotNull final Project project, @NotNull final String message, final MessageType messageType) {
    this(project, message, messageType, true);
  }

  public VcsBalloonProblemNotifier(@NotNull final Project project, @NotNull final String message, final MessageType messageType, boolean showOverChangesView) {
    myProject = project;
    myMessage = message;
    myMessageType = messageType;
    myShowOverChangesView = showOverChangesView;
  }

  public static void showOverChangesView(@NotNull final Project project, @NotNull final String message, final MessageType type) {
    show(project, message, type, true);
  }

  public static void showOverVersionControlView(@NotNull final Project project, @NotNull final String message, final MessageType type) {
    show(project, message, type, false);
  }

  private static void show(final Project project, final String message, final MessageType type, final boolean showOverChangesView) {
    final Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment()) return;
    final Runnable showErrorAction = new Runnable() {
      public void run() {
        new VcsBalloonProblemNotifier(project, message, type, showOverChangesView).run();
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
    NOTIFICATION_GROUP.createNotification(myMessage, myMessageType).notify(myProject.isDefault() ? null : myProject);
  }

  public static void showBalloonForComponent(@NotNull JComponent component, @NotNull final String message, final MessageType type,
                                             final boolean atTop) {
      BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, type, null);
      Balloon balloon = balloonBuilder.createBalloon();
      Dimension size = component.getSize();
      Balloon.Position position;
      int x;
      int y;
      if (size == null) {
        x = y = 0;
        position = Balloon.Position.above;
      }
      else {
        x = Math.min(10, size.width / 2);
        y = size.height;
        position = Balloon.Position.below;
      }
      balloon.show(new RelativePoint(component, new Point(x, y)), position);
  }
}
