// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.widget;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationsToolWindowFactory;
import com.intellij.notification.impl.ui.NotificationsUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.IconLikeCustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class IdeNotificationArea extends JLabel implements CustomStatusBarWidget, IconLikeCustomStatusBarWidget {
  public static final String WIDGET_ID = "Notifications";

  private @Nullable StatusBar myStatusBar;

  public IdeNotificationArea() {
    setBorder(JBUI.CurrentTheme.StatusBar.Widget.iconBorder());
  }

  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @Override
  public void dispose() {
    myStatusBar = null;
    UIUtil.dispose(this);
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
    Project project = myStatusBar.getProject();
    if (project != null && !project.isDisposed()) {
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (!project.isDisposed()) {
            EventLog.toggleLog(project, null);
          }
          return true;
        }
      }.installOn(this, true);

      Application app = ApplicationManager.getApplication();
      app.getMessageBus().connect(this).subscribe(LogModel.LOG_MODEL_CHANGED, () -> app.invokeLater(() -> updateStatus(project)));
      updateStatus(project);
    }
  }

  @Override
  public @NotNull String ID() {
    return WIDGET_ID;
  }

  private void updateStatus(@Nullable Project project) {
    if (project == null || project.isDisposed()) {
      return;
    }
    List<Notification> notifications;
    if (ActionCenter.isEnabled()) {
      notifications = NotificationsToolWindowFactory.Companion.getStateNotifications(project);
    }
    else {
      notifications = EventLog.getLogModel(project).getNotifications();
    }
    updateIconOnStatusBar(notifications);

    int count = notifications.size();
    setToolTipText(count > 0 ? UIBundle.message("status.bar.notifications.widget.tooltip", count) 
                             : UIBundle.message("status.bar.notifications.widget.no.notification.tooltip"));
  }

  private void updateIconOnStatusBar(List<? extends Notification> notifications) {
    if (ActionCenter.isEnabled()) {
      setIcon(getActionCenterNotificationIcon(notifications));
    }
    else {
      setIcon(createIconWithNotificationCount(this, NotificationType.getDominatingType(notifications), notifications.size(), false));
    }
  }

  public static @NotNull Icon getActionCenterNotificationIcon(List<? extends Notification> notifications) {
    for (Notification notification : notifications) {
      if (notification.isSuggestionType() && notification.isImportantSuggestion() || notification.getType() == NotificationType.ERROR) {
        return AllIcons.Toolwindows.NotificationsNewImportant;
      }
    }
    return notifications.isEmpty() ? AllIcons.Toolwindows.Notifications : AllIcons.Toolwindows.NotificationsNew;
  }

  public static @NotNull LayeredIcon createIconWithNotificationCount(JComponent component, NotificationType type, int size, boolean forToolWindow) {
    LayeredIcon icon = new LayeredIcon(2);
    Icon baseIcon = getPendingNotificationsIcon(type, forToolWindow);
    icon.setIcon(baseIcon, 0);
    if (size > 0) {
      //noinspection UseJBColor
      Color textColor = type == NotificationType.ERROR || type == NotificationType.INFORMATION
                        ? new JBColor(Color.white, new Color(0xF2F2F2))
                        : new Color(0x333333);
      icon.setIcon(new TextIcon(component, size < 10 ? String.valueOf(size) : "9+", textColor, baseIcon), 1);
    }
    return icon;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  private static Icon getPendingNotificationsIcon(NotificationType maximumType, boolean forToolWindow) {
    if (maximumType != null) {
      switch (maximumType) {
        case WARNING:
          return forToolWindow ? AllIcons.Toolwindows.WarningEvents : AllIcons.Ide.Notification.WarningEvents;
        case ERROR:
          return forToolWindow ? AllIcons.Toolwindows.ErrorEvents : AllIcons.Ide.Notification.ErrorEvents;
        case INFORMATION:
        case IDE_UPDATE:
          return forToolWindow ? AllIcons.Toolwindows.InfoEvents : AllIcons.Ide.Notification.InfoEvents;
      }
    }
    return forToolWindow ? AllIcons.Toolwindows.NoEvents : AllIcons.Ide.Notification.NoEvents;
  }

  private static class TextIcon implements Icon {
    private final String myStr;
    private final JComponent myComponent;
    private final Color myTextColor;
    private final Icon myBaseIcon;
    private final int myWidth;
    private final Font myFont;

    TextIcon(JComponent component, @NotNull String str, @NotNull Color textColor, @NotNull Icon baseIcon) {
      myStr = str;
      myComponent = component;
      myTextColor = textColor;
      myBaseIcon = baseIcon;
      myFont = new Font(NotificationsUtil.getFontName(), Font.BOLD, JBUIScale.scale(9));
      myWidth = myComponent.getFontMetrics(myFont).stringWidth(myStr);
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
      Graphics2D g2 = (Graphics2D)g;
      UISettings.setupAntialiasing(g);

      Font originalFont = g.getFont();
      Color originalColor = g.getColor();
      g.setFont(myFont);

      FontMetrics fm = SwingUtilities2.getFontMetrics((JComponent)c, g);
      boolean isTwoChar = myStr.length() == 2;

      float center = getIconWidth() - fm.stringWidth(myStr) + (isTwoChar ? JBUIScale.scale(1) : 0);
      float textX = x + center / 2;
      float textY = y + SimpleColoredComponent.getTextBaseLine(fm, getIconHeight());

      if (!JreHiDpiUtil.isJreHiDPI(g2)) {
        textX = (float)Math.floor(textX);
      }

      g.setColor(myTextColor);
      g2.drawString(myStr.substring(0, 1), textX, textY);

      if (isTwoChar) {
        textX += fm.charWidth(myStr.charAt(0)) - JBUIScale.scale(1);
        g2.drawString(myStr.substring(1), textX, textY);
      }

      g.setFont(originalFont);
      g.setColor(originalColor);
    }

    @Override
    public int getIconWidth() {
      return myBaseIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return myBaseIcon.getIconHeight();
    }
  }
}
