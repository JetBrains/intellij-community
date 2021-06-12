package com.intellij.jps.cache;

import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.server.BuildManager;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import static com.intellij.jps.cache.ui.JpsLoaderNotifications.ATTENTION;

final class JpsCacheStartupActivity implements StartupActivity.DumbAware {
  private static final String NOT_ASK_AGAIN = "JpsCaches.NOT_ASK_AGAIN";

  @Override
  public void runActivity(@NotNull Project project) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
    if (propertiesComponent.getBoolean(NOT_ASK_AGAIN)) {
      return;
    }

    CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project);
    if (!workspaceConfiguration.MAKE_PROJECT_ON_SAVE || !TrustedProjects.isTrusted(project)) {
      return;
    }

    ATTENTION
      .createNotification(JpsCacheBundle.message("notification.title.automatic.project.build.enabled"), JpsCacheBundle.message("notification.content.make.project.automatically.enabled.affect.caches"), NotificationType.WARNING)
      .addAction(NotificationAction.createSimpleExpiring(JpsCacheBundle.message("action.NotificationAction.JpsCachesDummyProjectComponent.text.disable.property"), () -> {
        workspaceConfiguration.MAKE_PROJECT_ON_SAVE = false;
        BuildManager.getInstance().clearState(project);
      }))
      .addAction(NotificationAction.createSimpleExpiring(JpsCacheBundle.message("action.NotificationAction.JpsCachesDummyProjectComponent.text.dont.ask"), () -> {
        propertiesComponent.setValue(NOT_ASK_AGAIN, true);
      }))
      .notify(project);
  }
}
