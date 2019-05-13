package org.editorconfig.plugincomponents;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/**
 * @author Dennis.Ushakov
 */
public class EditorConfigNotifier {
  public static final String LAST_NOTIFICATION_STATUS = "editorconfig.notification";

  public static EditorConfigNotifier getInstance() {
    return ServiceManager.getService(EditorConfigNotifier.class);
  }

  public void error(Project project, String id, String message) {
    doNotify(project, id, message, NotificationType.ERROR);
  }

  protected void doNotify(Project project, String id, String message, final NotificationType type) {
    final String value = PropertiesComponent.getInstance(project).getValue("editorconfig.notification");
    if (id.equals(value)) return;
    Notifications.Bus.notify(new Notification("editorconfig", "EditorConfig", message, type), project);
    PropertiesComponent.getInstance(project).setValue(LAST_NOTIFICATION_STATUS, id);
  }
}
