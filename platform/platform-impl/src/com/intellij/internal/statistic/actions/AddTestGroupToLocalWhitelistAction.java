// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.internal.statistic.eventLog.whitelist.WhitelistTestGroupStorage;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class AddTestGroupToLocalWhitelistAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    final AddGroupToLocalWhitelistDialog dialog = new AddGroupToLocalWhitelistDialog(project);
    final boolean result = dialog.showAndGet();
    if (!result || StringUtil.isEmpty(dialog.getGroupId()) || StringUtil.isEmpty(dialog.getRecorderId())) {
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Adding Test Group and Updating Whitelist...", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final String recorderId = dialog.getRecorderId();
        final WhitelistTestGroupStorage testWhitelist = WhitelistTestGroupStorage.getInstance(recorderId);
        try {
          if (dialog.isCustomRules()) {
            testWhitelist.addGroupWithCustomRules(dialog.getGroupId(), dialog.getCustomRules());
          }
          else {
            testWhitelist.addTestGroup(dialog.getGroupId());
          }
          showNotification(project, NotificationType.INFORMATION, "Group '" + dialog.getGroupId() + "' was added to local whitelist");
        }
        catch (IOException ex) {
          showNotification(project, NotificationType.ERROR, "Failed updating local list: " + ex.getMessage());
        }
      }
    });
  }

  protected void showNotification(@NotNull Project project,
                                  @NotNull NotificationType type,
                                  @NotNull String message) {
    Notifications.Bus.notify(new Notification("FeatureUsageStatistics", "Feature Usage Statistics", message, type), project);
  }
}
