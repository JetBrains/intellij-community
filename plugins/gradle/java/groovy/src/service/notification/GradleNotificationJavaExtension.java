// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.groovy.service.notification;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.notification.GradleNotificationExtension;

/**
 * @author Vladislav.Soroka
 */
public final class GradleNotificationJavaExtension extends GradleNotificationExtension {
  @Override
  public boolean isInternalError(@NotNull Throwable error) {
    return false;
  }

  @Override
  protected void updateNotification(@NotNull NotificationData notificationData,
                                    @NotNull Project project,
                                    @NotNull ExternalSystemException e) {
    for (String fix : e.getQuickFixes()) {
      if (ApplyGradlePluginCallback.ID.equals(fix)) {
        notificationData.setListener(ApplyGradlePluginCallback.ID, new ApplyGradlePluginCallback(notificationData, project));
      }
    }
  }
}
