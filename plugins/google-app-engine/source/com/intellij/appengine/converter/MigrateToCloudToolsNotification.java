// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.converter;

import com.intellij.appengine.JavaGoogleAppEngineBundle;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.Month;

public class MigrateToCloudToolsNotification implements StartupActivity.DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    ProjectFacetManager facetManager = ProjectFacetManager.getInstance(project);
    if (facetManager.hasFacets(AppEngineFacet.ID) && isAppEngineSdkDeprecated()) {
      String text = JavaGoogleAppEngineBundle.message("migrate.to.google.cloud.notification.text");
      Notification notification = new Notification("Migrate to Google Cloud SDK",
                                                   JavaGoogleAppEngineBundle.message("notification.title.app.engine.sdk.detected"),
                                                   text, NotificationType.WARNING)
        .setListener(new NotificationListener.UrlOpeningListener(false));
      /*
      //currently Google Cloud Tools plugin doesn't provide seamless migration for all project, so we don't want to provide a direct link which installs it.
      notification.addAction(new AnAction("Install 'Google Cloud Tools' plugin") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          PluginsAdvertiser.installAndEnablePlugins(ContainerUtil.set("com.google.gct.core", "com.google.gct.login"),
                                                    () -> {
                                                      notification.expire();
                                                      PluginManagerCore.disablePlugin("com.intellij.appengine");
                                                    });
        }
      });
      */
      notification.notify(project);
    }
  }

  private static boolean isAppEngineSdkDeprecated() {
    return SystemProperties.getBooleanProperty("idea.enable.migration.to.google.cloud.sdk", LocalDate.now().isAfter(LocalDate.of(2017, Month.MAY, 5)));
  }
}
