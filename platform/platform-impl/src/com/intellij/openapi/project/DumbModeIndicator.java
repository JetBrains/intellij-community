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
package com.intellij.openapi.project;

import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DumbModeIndicator extends AbstractProjectComponent {
  private static final String DUMB_MODE_MESSAGE = "Updating project indices...<br>\n" +
                                                  "Refactorings, usage search and some other features will become available after indexing is complete";
  private final Alarm myAlarm;

  public DumbModeIndicator(Project project) {
    super(project);
    myAlarm = new Alarm(project);
  }

  public void projectOpened() {
    myProject.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      boolean myFirstNotification = true; // first dumb mode is always a "forced" dumb mode including initial project scan
      boolean myLogged;
      BalloonHandler myHandler;

      public void enteredDumbMode() {
        final boolean first = myFirstNotification;
        myFirstNotification = false;
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            Notifications.Bus.logEvent(DUMB_MODE_MESSAGE, NotificationType.INFORMATION, myProject);
            myLogged = true;
          }
        }, 100);
        myAlarm.addRequest(new Runnable() {
          public void run() {
            if (!NotificationsManagerImpl.isEventLogVisible(myProject)) {
              myHandler = DumbService.getInstance(myProject).showDumbModeNotification(DUMB_MODE_MESSAGE);
            }
          }
        }, first? 10000 : 1000);
      }

      public void exitDumbMode() {
        myAlarm.cancelAllRequests();
        if (myHandler != null) myHandler.hide();
        if (myLogged) {
          Notifications.Bus.logEvent("Indexing finished", NotificationType.INFORMATION, myProject);
        }
        myLogged = false;
        myHandler = null;
        FileEditorManagerEx.getInstanceEx(myProject).refreshIcons();
      }
    });
  }

  @NotNull
  public String getComponentName() {
    return DumbModeIndicator.class.getSimpleName();
  }
}
