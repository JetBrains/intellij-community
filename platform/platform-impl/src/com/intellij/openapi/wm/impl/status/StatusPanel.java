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

import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Date;

/**
 * @author peter
 */
class StatusPanel extends JPanel {
  private static final Icon ourShowLogIcon = IconLoader.getIcon("/general/hideSideUp.png");
  private static final Icon ourHideLogIcon = IconLoader.getIcon("/general/hideSideDown.png");
  private boolean myLogMode;
  private String myLogMessage;
  private Date myLogTime;
  private boolean myDirty;
  private final Alarm myLogAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final TextPanel myTextPanel = new TextPanel();
  private final JLabel myShowLog = new JLabel();

  StatusPanel() {
    super(new BorderLayout());

    myShowLog.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final ToolWindow eventLog = getEventLog();
        if (eventLog != null) {
          if (!eventLog.isVisible()) {
            eventLog.activate(null, true);
          } else {
            eventLog.hide(null);
          }
        }
      }
    });

    add(myShowLog, BorderLayout.WEST);
    add(myTextPanel, BorderLayout.CENTER);
  }

  @Nullable
  private ToolWindow getEventLog() {
    // a better way of finding a project would be great
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      final JComponent frame = WindowManager.getInstance().getIdeFrame(project).getComponent();
      if (SwingUtilities.isDescendingFrom(this, frame)) {
        return ToolWindowManager.getInstance(project).getToolWindow(NotificationsManagerImpl.LOG_TOOL_WINDOW_ID);
      }
    }
    return null;
  }

  public void setLogMessage(String text) {
    myLogMessage = text;
    myLogTime = new Date();
    myDirty = false;
  }

  public void updateText(boolean logMode, @Nullable String nonLogText) {
    myLogMode = logMode;

    myShowLog.setVisible(logMode);

    if (logMode) {
      new Runnable() {
        @Override
        public void run() {
          String text = myLogMessage;
          if (myLogTime != null && (myDirty || System.currentTimeMillis() - myLogTime.getTime() >= DateFormatUtil.MINUTE)) {
            text += " (" + StringUtil.decapitalize(DateFormatUtil.formatPrettyDateTime(myLogTime)) + ")";
          }
          myTextPanel.setText(text);
          myLogAlarm.addRequest(this, 30000);
        }
      }.run();
      new Runnable() {
        @Override
        public void run() {
          final ToolWindow eventLog = getEventLog();
          if (eventLog == null) {
            myShowLog.setVisible(false);
            return;
          }

          final boolean visible = eventLog.isVisible();
          myShowLog.setIcon(visible ? ourHideLogIcon : ourShowLogIcon);
          myShowLog.setToolTipText(visible ? "" : "Click to open the event log");

          myLogAlarm.addRequest(this, 50);
        }
      }.run();
    } else {
      myDirty = true;
      myTextPanel.setText(nonLogText);
      myLogAlarm.cancelAllRequests();
    }

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
}
