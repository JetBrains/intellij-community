/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Shows a notification balloon over one of version control related tool windows: Changes View or Version Control View.
 * By default the notification is shown over the Changes View.
 * Use the special method or supply additional parameter to the constructor to show the balloon over the Version Control View.
 */
public class VcsBalloonProblemNotifier implements Runnable {
  private final Project myProject;
  private final String myMessage;
  private final MessageType myMessageType;
  private final boolean myShowOverChangesView;

  public VcsBalloonProblemNotifier(final Project project, final String message, final MessageType messageType) {
    this(project, message, messageType, true);
  }

  public VcsBalloonProblemNotifier(final Project project, final String message, final MessageType messageType, boolean showOverChangesView) {
    myProject = project;
    myMessage = message;
    myMessageType = messageType;
    myShowOverChangesView = showOverChangesView;
  }

  public static void showOverChangesView(final Project project, final String message, final MessageType type) {
    show(project, message, type, true);
  }

  public static void showOverVersionControlView(final Project project, final String message, final MessageType type) {
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
      ApplicationManager.getApplication().invokeLater(showErrorAction);
    }
  }

  public void run() {
    final Collection<Project> projects;
    if (myProject != null) {
      projects = Collections.singletonList(myProject);
    } else {
      doForProject(null);
    }
  }

  private void doForProject(final Project project) {
    AbstractCommonUpdateAction.NOTIFICATION_GROUP.createNotification(myMessage, myMessageType).notify(project);
  }
}
