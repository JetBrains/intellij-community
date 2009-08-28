package com.intellij.notification.impl.actions;

import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class NotificationTestAction extends AnAction implements DumbAware {
  public NotificationTestAction() {
    super("Add Test Notification");
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final MessageBus messageBus = project == null ? ApplicationManager.getApplication().getMessageBus() : project.getMessageBus();

    final long l = System.currentTimeMillis();
    final NotificationType type;
    if (l % 3 == 0) {
      type = NotificationType.ERROR;
    } else if (l % 5 == 0) {
      type = NotificationType.WARNING;
    } else {
      type = NotificationType.INFORMATION;
    }

    messageBus.syncPublisher(Notifications.TOPIC).notify("Idea.Test", "Test Notification", "Test Notification Description", type,
        new NotificationListener() {
          @NotNull
          public Continue perform() {
            final int i = Messages.showChooseDialog("Notification message", "Test", new String[]{"Leave", "Remove"}, "Remove", null);
            return i == 1 ? Continue.REMOVE : Continue.LEAVE;
          }

          public Continue onRemove() {
            return Continue.REMOVE;
          }
        });
  }
}
