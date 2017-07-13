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
package org.jetbrains.plugins.gradle.util;

import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.initialization.BuildLayoutParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.*;

import static com.intellij.jarFinder.InternetAttachSourceProvider.attachSourceJar;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.attachSourcesAndJavadocFromGradleCacheIfNeeded;

/**
 * @author Vladislav.Soroka
 */
public class GradleAttachSourcesProvider implements AttachSourcesProvider {
  @NotNull
  @Override
  public Collection<AttachSourcesAction> getActions(List<LibraryOrderEntry> orderEntries, PsiFile psiFile) {
    Map<LibraryOrderEntry, Module> gradleModules = getGradleModules(orderEntries);
    if (gradleModules.isEmpty()) return Collections.emptyList();

    return Collections.singleton(new AttachSourcesAction() {
      @Override
      public String getName() {
        return GradleBundle.message("gradle.action.download.sources");
      }

      @Override
      public String getBusyText() {
        return GradleBundle.message("gradle.action.download.sources.busy.text");
      }

      @Override
      public ActionCallback perform(List<LibraryOrderEntry> orderEntries) {
        Map<LibraryOrderEntry, Module> gradleModules = getGradleModules(orderEntries);
        if (gradleModules.isEmpty()) return ActionCallback.REJECTED;
        final ActionCallback resultWrapper = new ActionCallback();
        Project project = psiFile.getProject();

        Map.Entry<LibraryOrderEntry, Module> next = gradleModules.entrySet().iterator().next();
        LibraryOrderEntry libraryOrderEntry = next.getKey();
        Module module = next.getValue();

        String libraryName = libraryOrderEntry.getLibraryName();
        if (libraryName == null) return ActionCallback.REJECTED;

        String artifactCoordinates = StringUtil.trimStart(libraryName, GradleConstants.SYSTEM_ID.getReadableName() + ": ");
        if (StringUtil.equals(libraryName, artifactCoordinates)) return ActionCallback.REJECTED;
        final String gradlePath = GradleProjectResolverUtil.getGradlePath(module);
        if (gradlePath == null) return ActionCallback.REJECTED;

        final String taskName = "Download sources";
        String initScript = "allprojects {\n" +
                            "  afterEvaluate { project ->\n" +
                            "    if(project.path == '" + gradlePath + "') {\n" +
                            "        project.configurations.maybeCreate('downloadSources')\n" +
                            "        project.dependencies.add('downloadSources', '" + artifactCoordinates + ":sources" + "')\n" +
                            "        project.tasks.create(name: '" + taskName + "', overwrite: true) {\n" +
                            "        doLast {\n" +
                            "          project.configurations.downloadSources.resolve()\n" +
                            "        }\n" +
                            "      }\n" +
                            "    }\n" +
                            "  }\n" +
                            "}\n";

        UserDataHolderBase userData = new UserDataHolderBase();
        userData.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, initScript);

        String gradleVmOptions = GradleSettings.getInstance(project).getGradleVmOptions();
        ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
        settings.setExecutionName("Download sources");
        settings.setExternalProjectPath(ExternalSystemApiUtil.getExternalRootProjectPath(module));
        settings.setTaskNames(ContainerUtil.list(taskName));
        settings.setVmOptions(gradleVmOptions);
        settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
        ExternalSystemUtil.runTask(
          settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
          new TaskCallback() {
            @Override
            public void onSuccess() {
              File sourceJar = getSourceFile(artifactCoordinates, libraryOrderEntry.getFiles(OrderRootType.CLASSES)[0], project);
              ApplicationManager.getApplication().invokeLater(() -> {
                final Set<Library> libraries = new HashSet<>();
                for (LibraryOrderEntry orderEntry : orderEntries) {
                  ContainerUtil.addIfNotNull(libraries, orderEntry.getLibrary());
                }
                attachSourceJar(sourceJar, libraries);
                resultWrapper.setDone();
              });
            }

            @Override
            public void onFailure() {
              resultWrapper.setRejected();
              String message = ("<html>Sources not found for: " + artifactCoordinates) + "</html>";
              NotificationData notification = new NotificationData(
                "Sources download failed", message, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC);
              ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification);
            }
          }, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false, userData);

        return resultWrapper;
      }
    });
  }

  @NotNull
  private static File getSourceFile(String artifactCoordinates, VirtualFile classesFile, Project project) {
    LibraryData data = new LibraryData(GradleConstants.SYSTEM_ID, artifactCoordinates);
    data.addPath(LibraryPathType.BINARY, PathUtil.getLocalFile(classesFile).getPath());
    String serviceDirectory = GradleSettings.getInstance(project).getServiceDirectoryPath();
    File gradleUserHome =
      serviceDirectory != null ? new File(serviceDirectory) : new BuildLayoutParameters().getGradleUserHomeDir();
    attachSourcesAndJavadocFromGradleCacheIfNeeded(gradleUserHome, data);
    return new File(data.getPaths(LibraryPathType.SOURCE).iterator().next());
  }

  private static Map<LibraryOrderEntry, Module> getGradleModules(List<LibraryOrderEntry> libraryOrderEntries) {
    Map<LibraryOrderEntry, Module> result = ContainerUtil.newHashMap();
    for (LibraryOrderEntry entry : libraryOrderEntries) {
      if (entry.isModuleLevel()) continue;
      Module module = entry.getOwnerModule();
      if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
        result.put(entry, module);
      }
    }
    return result;
  }
}
