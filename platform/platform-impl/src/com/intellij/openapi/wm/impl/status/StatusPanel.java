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
import com.intellij.notification.LogModel;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.IdeNotificationArea;
import com.intellij.notification.impl.NotificationModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * @author peter
 */
class StatusPanel extends JPanel {
  private static final Icon ourShowLogIcon = IconLoader.getIcon("/general/hideSideUp.png");
  private static final Icon ourHideLogIcon = IconLoader.getIcon("/general/hideSideDown.png");
  private boolean myLogMode;
  private boolean myDirty;
  private final Alarm myLogAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final TextPanel myTextPanel = new TextPanel();
  private final JLabel myShowLog = new JLabel();

  StatusPanel() {
    super(new BorderLayout());
    
    setOpaque(isOpaque() && !SystemInfo.isMac);

    myShowLog.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final ToolWindow eventLog = getEventLog();
        if (eventLog != null) {
          if (!eventLog.isVisible()) {
            eventLog.activate(null, true);
            EventLog.getLogModel(getActiveProject()).logShown();
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
  private Project getActiveProject() {
    // a better way of finding a project would be great
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      final JComponent frame = WindowManager.getInstance().getIdeFrame(project).getComponent();
      if (SwingUtilities.isDescendingFrom(this, frame)) {
        return project;
      }
    }
    return null;
  }

  @Nullable
  private ToolWindow getEventLog() {
    return EventLog.getEventLog(getActiveProject());
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
    myLogMode = logAllowed && StringUtil.isEmpty(nonLogText) && project != null;

    myShowLog.setVisible(myLogMode);

    if (myLogMode) {
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
      new Runnable() {
        @Override
        public void run() {
          final ToolWindow eventLog = getEventLog();
          if (eventLog == null) {
            myShowLog.setVisible(false);
            return;
          }

          assert project != null;

          final boolean visible = eventLog.isVisible();
          LogModel logModel = EventLog.getLogModel(project);
          if (visible) {
            logModel.logShown();
          }

          ArrayList<Notification> notifications = logModel.getNotifications();

          final int count = notifications.size();

          final NotificationType maximumType = count > 0 ? NotificationModel.getMaximumType(notifications) : null;

          Icon icon = visible
                      ? ourHideLogIcon
                      : maximumType == null
                        ? ourShowLogIcon
                        : statusMessage != null && maximumType == statusMessage.getType()
                          ? IdeNotificationArea.getPendingNotificationsIcon(ourShowLogIcon, maximumType)
                          : new PendingNotificationsIcon(ourShowLogIcon, maximumType == NotificationType.ERROR ? Color.red : maximumType == NotificationType.WARNING ? Color.orange : Color.green.darker());
          myShowLog.setIcon(icon);
          myShowLog.setToolTipText(visible ? "" : count > 0 ? count + " " + StringUtil.pluralize("notification", count) + " pending" : "Click to open the event log");
          myShowLog.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 20 - icon.getIconWidth()));


          myLogAlarm.addRequest(this, 50);
        }
      }.run();
    } else {
      myDirty = true;
      myTextPanel.setText(nonLogText);
      myLogAlarm.cancelAllRequests();
    }
    return myLogMode;
  }

  private static class PendingNotificationsIcon implements Icon {
    private final Icon myBase;
    private final Color myColor;

    private PendingNotificationsIcon(Icon base, Color color) {
      myBase = base;
      myColor = color;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      myBase.paintIcon(c, g, x, y);
      g.setColor(Color.white);
      int size = 4;
      g.drawRect(x, y, size + 1, size + 1);
      g.setColor(myColor);
      g.fillRect(x + 1, y + 1, size, size);
    }

    @Override
    public int getIconWidth() {
      return myBase.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return myBase.getIconHeight();
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
