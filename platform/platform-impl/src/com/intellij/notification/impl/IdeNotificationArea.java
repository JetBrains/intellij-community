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
package com.intellij.notification.impl;

import com.intellij.ide.*;
import com.intellij.notification.*;
import com.intellij.notification.impl.ui.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.*;

/**
 * @author spleaner
 */
public class IdeNotificationArea implements StatusBarWidget, StatusBarWidget.IconPresentation, NotificationModelListener {
  private static final Icon EMPTY_ICON = IconLoader.getIcon("/ide/notifications.png");
  private static final Icon ERROR_ICON = IconLoader.getIcon("/ide/error_notifications.png");
  private static final Icon WARNING_ICON = IconLoader.getIcon("/ide/warning_notifications.png");
  private static final Icon INFO_ICON = IconLoader.getIcon("/ide/info_notifications.png");

  private WeakReference<JBPopup> myPopupRef;
  private Icon myCurrentIcon = EMPTY_ICON;
  private StatusBar myStatusBar;

  public IdeNotificationArea() {
  }

  public Presentation getPresentation(@NotNull Type type) {
    return this;
  }

  public void dispose() {
    getManager().removeListener(this); // clean up
    cancelPopup();
  }

  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
    getManager().addListener(this);
  }

  private void cancelPopup() {
    if (myPopupRef != null) {
      final JBPopup popup = myPopupRef.get();
      if (popup != null) {
        popup.cancel();
      }

      myPopupRef = null;
    }
  }

  @Nullable
  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext((Component) myStatusBar));
  }

  public Consumer<MouseEvent> getClickConsumer() {
    return new Consumer<MouseEvent>() {
      public void consume(MouseEvent mouseEvent) {
        toggleList();
      }
    };
  }

  private static NotificationsManagerImpl getManager() {
    return NotificationsManagerImpl.getNotificationsManagerImpl();
  }

  private void toggleList() {
    JBPopup popup = null;
    if (myPopupRef != null) {
      popup = myPopupRef.get();
      myPopupRef = null;
    }

    if (popup != null && popup.isVisible()) {
      popup.cancel();
    } else {
      myPopupRef = new WeakReference<JBPopup>(NotificationsListPanel.show(getProject(), (JComponent) myStatusBar));
    }
  }

  @NotNull
  public Icon getIcon() {
    return myCurrentIcon;
  }

  public String getTooltipText() {
    final NotificationsManagerImpl manager = NotificationsManagerImpl.getNotificationsManagerImpl();
    if (manager.hasNotifications(getProject())) {
      final int count = manager.count(getProject());
      return String.format("%s notification%s pending", count, count == 1 ? "" : "s");
    }

    return "No new notifications";
  }

  @NotNull
  public String ID() {
    return "Notifications";
  }

  public void updateStatus() {
    final NotificationsManagerImpl manager = getManager();

    Icon icon = EMPTY_ICON;
    final NotificationType maximumType = manager.getMaximumType(getProject());
    if (maximumType != null) {
      switch (maximumType) {
        case WARNING:
          icon = WARNING_ICON;
          break;
        case ERROR:
          icon = ERROR_ICON;
          break;
        case INFORMATION:
        default:
          icon = INFO_ICON;
          break;
      }
    }

    myCurrentIcon = icon;
    if (manager.count(getProject()) == 0) {
      cancelPopup();
    }

    myStatusBar.updateWidget(ID());
  }

  public void notificationsAdded(@NotNull final Notification... notifications) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        updateStatus();
      }
    });
  }

  public void notificationsRead(@NotNull Notification... notification) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        updateStatus();
      }
    });
  }

  @SuppressWarnings({"SSBasedInspection"})
  public void notificationsRemoved(@NotNull final Notification... notifications) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        updateStatus();
      }
    });
  }
}
