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
package com.intellij.openapi.wm.impl.status;

import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author peter
 */
class StatusPanel {
  private boolean myLogMode;
  private boolean myDirty;
  private final Alarm myLogAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final TextPanel myTextPanel = new TextPanel();

  StatusPanel() {
    myTextPanel.setBorder(new EmptyBorder(0, 5, 0, 0));
    myTextPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (myLogMode) {
          EventLog.toggleLog(getActiveProject());
        }
      }
    });
  }

  @Nullable
  private Project getActiveProject() {
    // a better way of finding a project would be great
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      final JComponent frame = WindowManager.getInstance().getIdeFrame(project).getComponent();
      if (SwingUtilities.isDescendingFrom(myTextPanel, frame)) {
        return project;
      }
    }
    return null;
  }

  public void setLogMessage(String text) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myDirty = false;

    updateText(StringUtil.isNotEmpty(text), "");
  }

  public boolean updateText(boolean logAllowed, @Nullable String nonLogText) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Project project = getActiveProject();
    final Notification statusMessage = EventLog.getStatusMessage(project);
    myLogMode = logAllowed && StringUtil.isEmpty(nonLogText) && statusMessage != null;

    if (myLogMode) {
      myTextPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      new Runnable() {
        @Override
        public void run() {
          String text;
          if (statusMessage != null) {
            text = EventLog.formatForLog(statusMessage).first;
            if (myDirty || System.currentTimeMillis() - statusMessage.getCreationTime() >= DateFormatUtil.MINUTE) {
              text += " (" + StringUtil.decapitalize(DateFormatUtil.formatPrettyDateTime(statusMessage.getCreationTime())) + ")";
            }
          } else {
            text = "";
          }
          myTextPanel.setText(text);
          myLogAlarm.addRequest(this, 30000);
        }
      }.run();
    } else {
      myTextPanel.setCursor(Cursor.getDefaultCursor());
      myDirty = true;
      myTextPanel.setText(nonLogText);
      myLogAlarm.cancelAllRequests();
    }
    return myLogMode;
  }

  public void hideLog() {
    if (myLogMode) {
      updateText(false, "");
    }
  }

  public void restoreLogIfNeeded() {
    myLogAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (StringUtil.isEmpty(myTextPanel.getText())) {
          updateText(true, "");
        }
      }
    }, 300);
  }

  public String getText() {
    return myTextPanel.getText();
  }

  public TextPanel geTextPanel() {
    return myTextPanel;
  }
}
