/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.project.data;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 7/17/2014
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class ExternalProjectDataService implements ProjectDataService<ExternalProject, Project> {
  private static final Logger LOG = Logger.getInstance(ExternalProjectDataService.class);

  @NotNull public static final Key<ExternalProject> KEY = Key.create(ExternalProject.class, ProjectKeys.TASK.getProcessingWeight() + 1);

  @NotNull private final Map<Pair<ProjectSystemId, File>, ExternalProject> myExternalRootProjects;

  @NotNull private ProjectDataManager myProjectDataManager;

  public ExternalProjectDataService(@NotNull ProjectDataManager projectDataManager) {
    myProjectDataManager = projectDataManager;
    myExternalRootProjects = new ConcurrentFactoryMap<Pair<ProjectSystemId, File>, ExternalProject>() {
      @Nullable
      @Override
      protected ExternalProject create(Pair<ProjectSystemId, File> key) {
        return new ExternalProjectSerializer().load(key.first, key.second);
      }

      @Override
      public ExternalProject put(Pair<ProjectSystemId, File> key, ExternalProject value) {
        new ExternalProjectSerializer().save(value);
        return super.put(key, value);
      }
    };
  }

  @NotNull
  @Override
  public Key<ExternalProject> getTargetDataKey() {
    return KEY;
  }

  public void importData(@NotNull final Collection<DataNode<ExternalProject>> toImport,
                         @NotNull final Project project,
                         final boolean synchronous) {
    if (toImport.size() != 1) {
      throw new IllegalArgumentException(
        String.format("Expected to get a single external project but got %d: %s", toImport.size(), toImport));
    }
    saveExternalProject(toImport.iterator().next().getData());
  }

  @Override
  public void removeData(@NotNull final Collection<? extends Project> modules, @NotNull Project project, boolean synchronous) {
  }

  @Nullable
  public ExternalProject getOrImportRootExternalProject(@NotNull Project project,
                                                        @NotNull ProjectSystemId systemId,
                                                        @NotNull File projectRootDir) {
    final ExternalProject externalProject = getRootExternalProject(systemId, projectRootDir);
    return externalProject != null ? externalProject : importExternalProject(project, systemId, projectRootDir);
  }

  @Nullable
  private ExternalProject importExternalProject(@NotNull final Project project,
                                                @NotNull final ProjectSystemId projectSystemId,
                                                @NotNull final File projectRootDir) {
    final Boolean result = UIUtil.invokeAndWaitIfNeeded(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        final Ref<Boolean> result = new Ref<Boolean>(false);
        if (project.isDisposed()) return false;

        final String linkedProjectPath = FileUtil.toCanonicalPath(projectRootDir.getPath());
        final ExternalProjectSettings projectSettings =
          ExternalSystemApiUtil.getSettings(project, projectSystemId).getLinkedProjectSettings(linkedProjectPath);
        if (projectSettings == null) {
          LOG.warn("Unable to get project settings for project path: " + linkedProjectPath);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Available projects paths: " + ContainerUtil.map(
              ExternalSystemApiUtil.getSettings(project, projectSystemId).getLinkedProjectsSettings(),
              new Function<ExternalProjectSettings, String>() {
                @Override
                public String fun(ExternalProjectSettings settings) {
                  return settings.getExternalProjectPath();
                }
              }));
          }
          return false;
        }

        final File projectFile = new File(linkedProjectPath);
        final String projectName;
        if (projectFile.isFile()) {
          projectName = projectFile.getParentFile().getName();
        }
        else {
          projectName = projectFile.getName();
        }

        // ask a user for the project import if auto-import is disabled
        if (!projectSettings.isUseAutoImport()) {
          String message = String.format("Project '%s' require synchronization with %s configuration. \nImport the project?",
                                         projectName, projectSystemId.getReadableName());
          int returnValue = Messages.showOkCancelDialog(
            message, "Import Project", CommonBundle.getOkButtonText(), CommonBundle.getCancelButtonText(), Messages.getQuestionIcon()
          );
          if (returnValue != Messages.OK) return false;
        }

        final String title = ExternalSystemBundle.message("progress.import.text", linkedProjectPath, projectSystemId.getReadableName());
        new Task.Modal(project, title, false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            if (project.isDisposed()) return;

            ExternalSystemNotificationManager.getInstance(project)
              .clearNotifications(null, NotificationSource.PROJECT_SYNC, projectSystemId);
            ExternalSystemResolveProjectTask task =
              new ExternalSystemResolveProjectTask(projectSystemId, project, linkedProjectPath, false);
            task.execute(indicator, ExternalSystemTaskNotificationListener.EP_NAME.getExtensions());
            if (project.isDisposed()) return;

            final Throwable error = task.getError();
            if (error != null) {
              ExternalSystemNotificationManager.getInstance(project)
                .processExternalProjectRefreshError(error, projectName, projectSystemId);
              return;
            }
            final DataNode<ProjectData> projectDataDataNode = task.getExternalProject();
            if (projectDataDataNode == null) return;

            final Collection<DataNode<ExternalProject>> nodes = ExternalSystemApiUtil.findAll(projectDataDataNode, KEY);
            if (nodes.size() != 1) {
              throw new IllegalArgumentException(
                String.format("Expected to get a single external project but got %d: %s", nodes.size(), nodes));
            }

            ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(new Runnable() {
              @Override
              public void run() {
                myProjectDataManager.importData(KEY, nodes, project, true);
              }
            });

            result.set(true);
          }
        }.queue();

        return result.get();
      }
    });

    return result ? getRootExternalProject(projectSystemId, projectRootDir) : null;
  }

  @Nullable
  public ExternalProject getRootExternalProject(@NotNull ProjectSystemId systemId, @NotNull File projectRootDir) {
    return myExternalRootProjects.get(Pair.create(systemId, projectRootDir));
  }

  public void saveExternalProject(@NotNull ExternalProject externalProject) {
    myExternalRootProjects.put(
      Pair.create(new ProjectSystemId(externalProject.getExternalSystemId()), externalProject.getProjectDir()),
      new DefaultExternalProject(externalProject)
    );
  }

  @Nullable
  public ExternalProject findExternalProject(@NotNull ExternalProject parentProject, @NotNull Module module) {
    String externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module);
    return externalProjectId != null ? findExternalProject(parentProject, externalProjectId) : null;
  }

  @Nullable
  private static ExternalProject findExternalProject(@NotNull ExternalProject parentProject, @NotNull String externalProjectId) {
    if (parentProject.getQName().equals(externalProjectId)) return parentProject;
    if (parentProject.getChildProjects().containsKey(externalProjectId)) {
      return parentProject.getChildProjects().get(externalProjectId);
    }
    for (ExternalProject externalProject : parentProject.getChildProjects().values()) {
      final ExternalProject project = findExternalProject(externalProject, externalProjectId);
      if (project != null) return project;
    }
    return null;
  }
}
