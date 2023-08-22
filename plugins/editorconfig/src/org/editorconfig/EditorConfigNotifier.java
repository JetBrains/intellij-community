// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.project.Project;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.Nls;

public final class EditorConfigNotifier {
  public static final String LAST_NOTIFICATION_STATUS = "editorconfig.notification";
  public static final String GROUP_DISPLAY_ID = "editorconfig";

  public static void error(Project project, String id, @Nls String message) {
    String value = PropertiesComponent.getInstance(project).getValue("editorconfig.notification");
    if (id.equals(value)) {
      return;
    }

    NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_DISPLAY_ID);
    Notifications.Bus.notify(group.createNotification(EditorConfigBundle.message("editorconfig"), message, NotificationType.ERROR),
                             project);
    PropertiesComponent.getInstance(project).setValue(LAST_NOTIFICATION_STATUS, id);
  }
}
