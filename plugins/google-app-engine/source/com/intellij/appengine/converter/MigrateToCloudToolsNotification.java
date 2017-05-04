/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.appengine.converter;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.Month;

/**
 * @author nik
 */
public class MigrateToCloudToolsNotification implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    ProjectFacetManager facetManager = ProjectFacetManager.getInstance(project);
    if (facetManager.hasFacets(AppEngineFacet.ID) && isAppEngineSdkDeprecated()) {
      String text = "<html><body><p>It looks like your project is using the App Engine SDK. While the App Engine SDK is still supported, Google " +
                    "recommends that you migrate to the <a href=\"https://cloud.google.com/sdk/\">Google Cloud SDK</a>. The Cloud SDK provides new " +
                    "features such as <a href=\"https://cloud.google.com/appengine/docs/flexible/\">App Engine Flexible</a> support and " +
                    "<a href=\"https://cloud.google.com/debugger/\">Google Cloud Debugger</a> integration.</p>" +
                    "<p><a href=\"https://cloud.google.com/tools/intellij/docs/migrate\">Learn more about migrating to the new Cloud tools</a>.</p>" +
                    "</body></html>";
      Notification notification = new Notification("Migrate to Google Cloud SDK", "App Engine SDK Detected",
                                                   text, NotificationType.WARNING, new NotificationListener.UrlOpeningListener(false));
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
