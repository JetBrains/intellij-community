// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogTestWhitelistPersistence;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
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
        final SensitiveDataValidator validator = SensitiveDataValidator.getInstance(recorderId);

        validator.update();
        try {
          if (dialog.isCustomRules()) {
            EventLogTestWhitelistPersistence.addGroupWithCustomRules(recorderId, dialog.getGroupId(), dialog.getCustomRules());
          }
          else {
            EventLogTestWhitelistPersistence.addTestGroup(recorderId, dialog.getGroupId(), dialog.getEventData());
          }
          validator.reload();
          showNotification(project, e, MessageType.INFO, "Group '" + dialog.getGroupId() + "' was added to local whitelist");
        }
        catch (IOException ex) {
          showNotification(project, e, MessageType.ERROR, "Failed updating local list: " + ex.getMessage());
        }
      }
    });
  }

  protected void showNotification(@NotNull Project project,
                                  @NotNull AnActionEvent event,
                                  @NotNull MessageType type,
                                  @NotNull String message) {
    ApplicationManager.getApplication().invokeLater(() -> JBPopupFactory.getInstance().
      createHtmlTextBalloonBuilder(message, type, null).
      setFadeoutTime(2000).setDisposable(project).createBalloon().
      show(JBPopupFactory.getInstance().guessBestPopupLocation(event.getDataContext()), Balloon.Position.below));
  }
}
