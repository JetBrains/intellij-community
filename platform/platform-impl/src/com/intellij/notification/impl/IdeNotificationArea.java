/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.notification.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.notification.EventLog;
import com.intellij.notification.LogModel;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.IconLikeCustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.ClickListener;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class IdeNotificationArea extends JLabel implements CustomStatusBarWidget, IconLikeCustomStatusBarWidget {
  public static final String WIDGET_ID = "Notifications";
  private StatusBar myStatusBar;

  public IdeNotificationArea() {
    UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        updateStatus();
      }
    }, this);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        EventLog.toggleLog(getProject(), null);
        return true;
      }
    }.installOn(this);

    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(LogModel.LOG_MODEL_CHANGED, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            updateStatus();
          }
        });
      }
    });
  }

  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  public void dispose() {
  }

  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
    updateStatus();
  }

  @Nullable
  private Project getProject() {
    return CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext((Component)myStatusBar));
  }

  @NotNull
  public String ID() {
    return WIDGET_ID;
  }

  private void updateStatus() {
    final Project project = getProject();
    ArrayList<Notification> notifications = EventLog.getLogModel(project).getNotifications();
    applyIconToStatusAndToolWindow(project, createIconWithNotificationCount(notifications));

    int count = notifications.size();
    setToolTipText(count > 0 ? String.format("%s notification%s pending", count, count == 1 ? "" : "s") : "No new notifications");

    myStatusBar.updateWidget(ID());
  }

  private void applyIconToStatusAndToolWindow(Project project, LayeredIcon icon) {
    if (UISettings.getInstance().HIDE_TOOL_STRIPES || UISettings.getInstance().PRESENTATION_MODE) {
      setVisible(true);
      setIcon(icon);
    }
    else {
      ToolWindow eventLog = EventLog.getEventLog(project);
      if (eventLog != null) {
        eventLog.setIcon(icon);
      }
      setVisible(false);
    }
  }

  private LayeredIcon createIconWithNotificationCount(ArrayList<Notification> notifications) {
    LayeredIcon icon = new LayeredIcon(2);
    NotificationType type = getMaximumType(notifications);
    Icon statusIcon = getPendingNotificationsIcon(AllIcons.Ide.Notification.NoEvents, type);
    icon.setIcon(statusIcon, 0);
    int size = notifications.size();
    if (size > 0) {
      //noinspection UseJBColor
      Color textColor = type == NotificationType.ERROR ? Color.white : Color.black;
      icon.setIcon(new TextIcon(this, size < 10 ? String.valueOf(size) : "9+", textColor), 1);
    }
    return icon;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  private static Icon getPendingNotificationsIcon(Icon defIcon, final NotificationType maximumType) {
    if (maximumType != null) {
      switch (maximumType) {
        case WARNING:
          return AllIcons.Ide.Notification.WarningEvents;
        case ERROR:
          return AllIcons.Ide.Notification.ErrorEvents;
        case INFORMATION:
          return AllIcons.Ide.Notification.InfoEvents;
      }
    }
    return defIcon;
  }

  @Nullable
  private static NotificationType getMaximumType(List<Notification> notifications) {
    NotificationType result = null;
    for (Notification notification : notifications) {
      if (NotificationType.ERROR == notification.getType()) {
        return NotificationType.ERROR;
      }

      if (NotificationType.WARNING == notification.getType()) {
        result = NotificationType.WARNING;
      }
      else if (result == null && NotificationType.INFORMATION == notification.getType()) {
        result = NotificationType.INFORMATION;
      }
    }

    return result;
  }

  private static class TextIcon implements Icon {
    private final String myStr;
    private final JComponent myComponent;
    private final Color myTextColor;
    private final int myWidth;

    public TextIcon(JComponent component, @NotNull String str, @NotNull Color textColor) {
      myStr = str;
      myComponent = component;
      myTextColor = textColor;
      myWidth = myComponent.getFontMetrics(calcFont()).stringWidth(myStr);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TextIcon)) return false;

      TextIcon icon = (TextIcon)o;

      if (myWidth != icon.myWidth) return false;
      if (!myComponent.equals(icon.myComponent)) return false;
      if (!myStr.equals(icon.myStr)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myStr.hashCode();
      result = 31 * result + myComponent.hashCode();
      result = 31 * result + myWidth;
      return result;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      UISettings.setupAntialiasing(g);

      Font originalFont = g.getFont();
      Color originalColor = g.getColor();
      g.setFont(calcFont());

      x += (getIconWidth() - myWidth) / 2;
      y += getIconHeight() / 2 + g.getFontMetrics().getDescent();
      if (!SystemInfo.isLinux && myStr.length() > 1) {
        x++;
      }

      g.setColor(myTextColor);
      g.drawString(myStr, x, y);

      g.setFont(originalFont);
      g.setColor(originalColor);
    }

    private Font calcFont() {
      float size = (float)getIconHeight() * 3 / 5;
      if (myStr.length() > 1) {
        size--;
      }
      return myComponent.getFont().deriveFont(size);
    }

    @Override
    public int getIconWidth() {
      return AllIcons.Ide.Notification.NoEvents.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return AllIcons.Ide.Notification.NoEvents.getIconHeight();
    }
  }
}
