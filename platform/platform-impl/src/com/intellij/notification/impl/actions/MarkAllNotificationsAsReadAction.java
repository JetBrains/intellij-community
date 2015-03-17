package com.intellij.notification.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.notification.EventLog;
import com.intellij.notification.LogModel;
import com.intellij.notification.Notification;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;

public class MarkAllNotificationsAsReadAction extends DumbAwareAction {
  public MarkAllNotificationsAsReadAction() {
    super("Mark all notifications as read", "Mark all unread notifications as read", AllIcons.Actions.Selectall);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(!EventLog.getLogModel(e.getData(CommonDataKeys.PROJECT)).getNotifications().isEmpty());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    LogModel model = EventLog.getLogModel(e.getData(CommonDataKeys.PROJECT));
    for (Notification notification : model.getNotifications()) {
      model.removeNotification(notification);
      notification.expire();
    }
  }
}
