// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

final class GradleStartupActivity implements StartupActivity.DumbAware {
  private static final Logger LOG = Logger.getInstance(GradleStartupActivity.class);

  private static final Key<Boolean> ASKED_SCANS_POPUP_QUESTION = Key.create("gradle.scans.popup.question");

  @Override
  public void runActivity(@NotNull final Project project) {
    ExternalProjectsManager.getInstance(project).runWhenInitialized(() -> {
      GradleExtensionsSettings.load(project);
      GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(project.getBasePath());
      if (!projectSettings.isScansEnabled() && project.getUserData(ASKED_SCANS_POPUP_QUESTION) != Boolean.TRUE) {
        Notification notification = GradleNotification.NOTIFICATION_GROUP.createNotification(
          "Enable build scans",
          "Would you like to enable build scans for this project?",
          NotificationType.INFORMATION);
        notification.addAction(new AnAction("Yes") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            projectSettings.setScansEnabled(true);
            project.putUserData(ASKED_SCANS_POPUP_QUESTION, Boolean.TRUE);
            notification.expire();
          }
        });
        notification.addAction(new AnAction("No") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            projectSettings.setScansEnabled(false);
            project.putUserData(ASKED_SCANS_POPUP_QUESTION, Boolean.TRUE);
            notification.expire();
          }
        });
        notification.notify(project);
      }
    });
  }
}
