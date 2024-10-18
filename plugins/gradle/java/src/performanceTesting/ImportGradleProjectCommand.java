// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DisposeAwareRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.plugins.gradle.service.project.open.GradleProjectImportUtil;
import org.jetbrains.plugins.gradle.settings.GradleDefaultProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleJvmResolutionUtil;
import org.jetbrains.plugins.gradle.util.GradleJvmValidationUtil;
import org.jetbrains.plugins.gradle.util.SuggestGradleVersionOptions;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jetbrains.plugins.gradle.util.GradleJvmSupportMatrices.suggestGradleVersion;

public final class ImportGradleProjectCommand extends AbstractCommand {
  public static final String PREFIX = "%importGradleProject";

  public ImportGradleProjectCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull PlaybackContext context) {
    ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    runWhenGradleImportAndIndexingFinished(context, actionCallback);
    return Promises.toPromise(actionCallback);
  }

  private void runWhenGradleImportAndIndexingFinished(@NotNull PlaybackContext context, @NotNull ActionCallback callback) {
    Project project = context.getProject();
    ExternalSystemProjectTrackerSettings projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(project);
    ExternalSystemProjectTrackerSettings.AutoReloadType currentAutoReloadType = projectTrackerSettings.getAutoReloadType();
    projectTrackerSettings.setAutoReloadType(ExternalSystemProjectTrackerSettings.AutoReloadType.NONE);
    context.message("Waiting for open and initialized Gradle project", getLine());
    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized(() -> DumbService.getInstance(project).runWhenSmart(() -> {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        waitForCurrentResolveTasks(context, project)
          .thenAsync(o -> {
            context.message("Import of the project has been started", getLine());
            AsyncPromise<Void> promise = new AsyncPromise<>();
            GradleSettings gradleSettings = GradleSettings.getInstance(project);
            linkGradleProjectIfNeeded(project, context, gradleSettings)
              .onError(throwable -> callback.reject("Link of a gradle project failed. Not a gradle project"))
              .onSuccess(unused -> doGradleSync(project, context, promise, gradleSettings, callback));
            return promise;
          })
          .onProcessed(promise -> {
            context.message("Import has been finished", getLine());
            projectTrackerSettings.setAutoReloadType(currentAutoReloadType);
            DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(() -> callback.setDone(), project));
          });
      });
    }));
  }

  private Promise<?> waitForCurrentResolveTasks(@NotNull PlaybackContext context,
                                                @NotNull Project project) {
    AsyncPromise<?> promise = new AsyncPromise<>();
    context.message("Waiting for current import resolve tasks", getLine());
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      var processingManager = ExternalSystemProcessingManager.getInstance();
      while (processingManager.hasTaskOfTypeInProgress(ExternalSystemTaskType.RESOLVE_PROJECT, project)) {
        final Object lock = new Object();
        synchronized (lock) {
          try {
            lock.wait(100);
          }
          catch (InterruptedException ignore) {
          }
        }
      }
      promise.setResult(null);
    });
    return promise.onProcessed(o -> {
      context.message("Import resolve tasks has been completed", getLine());
    });
  }

  private void doGradleSync(@NotNull Project project,
                            @NotNull PlaybackContext context,
                            @NotNull AsyncPromise<Void> promise,
                            @NotNull GradleSettings gradleSettings,
                            @NotNull ActionCallback callback) {
    Collection<GradleProjectSettings> projectsSettings = gradleSettings.getLinkedProjectsSettings();
    List<String> projectsPaths = ContainerUtil.map(projectsSettings, ExternalProjectSettings::getExternalProjectPath);
    AtomicInteger gradleProjectsToRefreshCount = new AtomicInteger(projectsSettings.size());
    StringBuilder projectsWithResolveErrors = new StringBuilder();
    for (GradleProjectSettings settings : projectsSettings) {
      ImportSpecBuilder importSpecBuilder = new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID);
      importSpecBuilder.callback(new ExternalProjectRefreshCallback() {
        private final ImportSpecBuilder.DefaultProjectRefreshCallback
          defaultCallback = new ImportSpecBuilder.DefaultProjectRefreshCallback(importSpecBuilder.build());

        @Override
        public void onSuccess(@Nullable DataNode<ProjectData> externalProject) {
          assert externalProject != null;
          context.message("Gradle resolve finished for: " + externalProject.getData().getLinkedExternalProjectPath(), getLine());
          SimpleMessageBusConnection connection = project.getMessageBus().simpleConnect();
          connection.subscribe(ProjectDataImportListener.TOPIC, new ProjectDataImportListener() {
            @Override
            public void onFinalTasksFinished(String projectPath) {
              _onImportFinished(projectPath);
            }

            @Override
            public void onImportFailed(String projectPath, @NotNull Throwable failure) {
              _onImportFinished(projectPath);
            }

            private void _onImportFinished(String projectPath) {
              if (!projectsPaths.contains(projectPath)) return;
              connection.disconnect();
              if (gradleProjectsToRefreshCount.decrementAndGet() == 0) {

                ApplicationManager.getApplication().invokeLater(() -> {
                  promise.setResult(null);
                });
              }
            }
          });

          defaultCallback.onSuccess(externalProject);
          callback.setDone();
        }

        @Override
        public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
          context.error("Gradle resolve failed for: " + settings.getExternalProjectPath() + ":" + errorMessage + ":" + errorDetails,
                        getLine());
          synchronized (projectsWithResolveErrors) {
            if (projectsWithResolveErrors.length() != 0) {
              projectsWithResolveErrors.append(", ");
            }
            projectsWithResolveErrors.append(String.format("'%s'", new File(settings.getExternalProjectPath()).getName()));
          }
          defaultCallback.onFailure(errorMessage, errorDetails);
          if (gradleProjectsToRefreshCount.decrementAndGet() == 0) {
            ApplicationManager.getApplication().invokeLater(() -> promise.setError(projectsWithResolveErrors.toString()));
            callback.reject("Gradle sync failed");
          }
        }
      });
      ExternalSystemUtil.refreshProject(settings.getExternalProjectPath(), importSpecBuilder);
    }
  }

  public static Promise<Void> linkGradleProjectIfNeeded(@NotNull Project project,
                                                        @NotNull PlaybackContext context,
                                                        @NotNull GradleSettings gradleSettings) {
    if (gradleSettings.getLinkedProjectsSettings().isEmpty()) {
      VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
      assert projectDir != null;
      VirtualFile[] children = projectDir.getChildren();
      boolean isGradleProject = ContainerUtil.exists(children, file -> GradleConstants.KNOWN_GRADLE_FILES.contains(file.getName()));
      if (!isGradleProject) {
        context.error("Unable to find Gradle project at " + projectDir.getPath(), 0);
        context.message("Files found at the path: " + Arrays.toString(ContainerUtil.map2Array(children, VirtualFile::getName)), 0);
        return Promises.rejectedPromise();
      }
      else {
        GradleProjectSettings projectSettings = GradleDefaultProjectSettings.createProjectSettings(projectDir.getPath());
        GradleProjectImportUtil.setupGradleSettings(gradleSettings);
        GradleVersion gradleVersion = suggestGradleVersion(
          new SuggestGradleVersionOptions()
            .withProject(project)
            .withProjectJdkVersionFilter(project)
        );
        if (gradleVersion != null) {
          GradleJvmResolutionUtil.setupGradleJvm(project, projectSettings, gradleVersion);
          GradleJvmValidationUtil.validateJavaHome(project, projectDir.toNioPath(), gradleVersion);
        }

        AsyncPromise<Void> promise = new AsyncPromise<>();
        ApplicationManager.getApplication().invokeLater(() -> {
          gradleSettings.linkProject(projectSettings);
          promise.setResult(null);
        });
        return promise;
      }
    }
    return Promises.resolvedPromise();
  }
}
