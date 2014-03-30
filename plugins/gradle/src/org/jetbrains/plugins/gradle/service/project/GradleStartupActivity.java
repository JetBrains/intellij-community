/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectImportBuilder;
import org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectImportProvider;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.FilenameFilter;

/**
 * @author Vladislav.Soroka
 * @since 12/10/13
 */
public class GradleStartupActivity implements StartupActivity {

  @NonNls private static final String SHOW_UNLINKED_GRADLE_POPUP = "show.inlinked.gradle.project.popup";
  private static final String IMPORT_EVENT_DESCRIPTION = "import";
  private static final String DO_NOT_SHOW_EVENT_DESCRIPTION = "do.not.show";

  @Override
  public void runActivity(@NotNull Project project) {
    configureBuildClasspath(project);
    showNotificationForUnlinkedGradleProject(project);
  }

  private static void configureBuildClasspath(@NotNull final Project project) {
    GradleBuildClasspathManager.getInstance(project).reload();
  }

  private static void showNotificationForUnlinkedGradleProject(@NotNull final Project project) {
    if (!PropertiesComponent.getInstance(project).getBoolean(SHOW_UNLINKED_GRADLE_POPUP, true)
        || !GradleSettings.getInstance(project).getLinkedProjectsSettings().isEmpty()
        || project.getUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT) == Boolean.TRUE
        || project.getBaseDir() == null) {
      return;
    }

    File baseDir = VfsUtilCore.virtualToIoFile(project.getBaseDir());
    final File[] files = baseDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return FileUtil.namesEqual(GradleConstants.DEFAULT_SCRIPT_NAME, name);
      }
    });

    if (files != null && files.length != 0) {
      String message = String.format("%s<br>\n%s",
                                     GradleBundle.message("gradle.notifications.unlinked.project.found.msg", IMPORT_EVENT_DESCRIPTION),
                                     GradleBundle.message("gradle.notifications.do.not.show"));

      GradleNotification.getInstance(project).showBalloon(
        GradleBundle.message("gradle.notifications.unlinked.project.found.title"),
        message, NotificationType.INFORMATION, new NotificationListener.Adapter() {
          @Override
          protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
            notification.expire();
            if (IMPORT_EVENT_DESCRIPTION.equals(e.getDescription())) {
              final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);
              GradleProjectImportBuilder gradleProjectImportBuilder = new GradleProjectImportBuilder(projectDataManager);
              final GradleProjectImportProvider gradleProjectImportProvider = new GradleProjectImportProvider(gradleProjectImportBuilder);
              AddModuleWizard wizard = new AddModuleWizard(null, files[0].getPath(), gradleProjectImportProvider);
              if ((wizard.getStepCount() <= 0 || wizard.showAndGet())) {
                ImportModuleAction.createFromWizard(project, wizard);
              }
            }
            else if (DO_NOT_SHOW_EVENT_DESCRIPTION.equals(e.getDescription())) {
              PropertiesComponent.getInstance(project).setValue(SHOW_UNLINKED_GRADLE_POPUP, Boolean.FALSE.toString());
            }
          }
        }
      );
    }
  }
}
