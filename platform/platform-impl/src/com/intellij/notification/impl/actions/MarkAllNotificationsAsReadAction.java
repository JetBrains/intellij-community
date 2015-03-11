package com.intellij.notification.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.notification.EventLog;
import com.intellij.notification.LogModel;
import com.intellij.notification.Notification;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;

public class MarkAllNotificationsAsReadAction extends DumbAwareAction {
  @Nullable
  private final Project myProject;

  @SuppressWarnings("unused")
  // needed for picoContainer
  public MarkAllNotificationsAsReadAction() {
    this(null);
  }

  public MarkAllNotificationsAsReadAction(@Nullable Project project) {
    super("Mark all notifications as read", "Mark all unread notifications as read", AllIcons.Actions.Selectall);
    myProject = project;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(!EventLog.getLogModel(getProject(e)).getNotifications().isEmpty());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    LogModel model = EventLog.getLogModel(getProject(e));
    for (Notification notification : model.getNotifications()) {
      model.removeNotification(notification);
      notification.expire();
    }
  }

  @Nullable
  private Project getProject(AnActionEvent e) {
    Project project = ObjectUtils.chooseNotNull(myProject, e.getData(CommonDataKeys.PROJECT));
    if (project != null && (project.isDisposed() || project.isDefault())) {
      return null;
    }
    return project;
  }
}
