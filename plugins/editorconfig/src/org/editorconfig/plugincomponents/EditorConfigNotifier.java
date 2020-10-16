// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.plugincomponents;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.Nls;

/**
 * @author Dennis.Ushakov
 */
public class EditorConfigNotifier {
  public static final String LAST_NOTIFICATION_STATUS = "editorconfig.notification";
  public static final String GROUP_DISPLAY_ID = "editorconfig";

  public static EditorConfigNotifier getInstance() {
    return ApplicationManager.getApplication().getService(EditorConfigNotifier.class);
  }

  public void error(Project project, String id, @Nls String message) {
    doNotify(project, id, message, NotificationType.ERROR);
  }

  protected void doNotify(Project project, String id, @Nls String message, final NotificationType type) {
    final String value = PropertiesComponent.getInstance(project).getValue("editorconfig.notification");
    if (id.equals(value)) return;
    Notifications.Bus.notify(new Notification(GROUP_DISPLAY_ID, EditorConfigBundle.message("editorconfig"), message, type), project);
    PropertiesComponent.getInstance(project).setValue(LAST_NOTIFICATION_STATUS, id);
  }
}
