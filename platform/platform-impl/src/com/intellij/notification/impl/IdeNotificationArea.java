package com.intellij.notification.impl;

import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.ui.NotificationComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.status.StatusBarImpl;
import com.intellij.openapi.wm.impl.status.StatusBarPatch;
import com.intellij.openapi.wm.impl.status.StatusBarTooltipper;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.ide.DataManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class IdeNotificationArea extends NotificationsBase implements StatusBarPatch {
  private NotificationComponent myNotificationComponent;

  public IdeNotificationArea(final StatusBarImpl statusBar) {
    myNotificationComponent = new NotificationComponent(this);

    StatusBarTooltipper.install(this, statusBar);
  }

  public JComponent getComponent() {
    return myNotificationComponent;
  }

  protected void doNotify(String id, String name, String description, NotificationType type, NotificationListener listener) {
    getManager().notify(new NotificationImpl(id, name, description, type, listener), getProject());
  }

  public void invalidateAll(@NotNull String id) {
    getManager().invalidateAll(id, getProject());
  }

  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    final NotificationsManager manager = getManager();

    if (manager.hasNotifications(getProject())) {
      final Notification notification = manager.getLatestNotification(getProject());
      return notification != null ? notification.getName() : null;
    }
    
    return null;
  }

  private static NotificationsManager getManager() {
    return NotificationsManager.getNotificationsManager();
  }

  public void clear() {
  }

  @Nullable
  public Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myNotificationComponent));
  }
}
