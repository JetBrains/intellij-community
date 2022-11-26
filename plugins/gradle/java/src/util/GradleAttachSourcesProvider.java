// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder;
import org.jetbrains.plugins.gradle.execution.target.GradleTargetUtil;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.execution.BuildLayoutParameters;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

import static com.intellij.jarFinder.InternetAttachSourceProvider.attachSourceJar;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.attachSourcesAndJavadocFromGradleCacheIfNeeded;

/**
 * @author Vladislav.Soroka
 */
final class GradleAttachSourcesProvider implements AttachSourcesProvider {

  @Override
  public @NotNull Collection<? extends AttachSourcesAction> getActions(@NotNull List<? extends LibraryOrderEntry> orderEntries,
                                                                       @NotNull PsiFile psiFile) {
    Map<LibraryOrderEntry, Module> gradleModules = getGradleModules(orderEntries);
    if (gradleModules.isEmpty()) return List.of();

    return List.of(new AttachSourcesAction() {
      @Override
      public String getName() {
        return GradleBundle.message("gradle.action.download.sources");
      }

      @Override
      public String getBusyText() {
        return GradleBundle.message("gradle.action.download.sources.busy.text");
      }

      @Override
      public @NotNull ActionCallback perform(@NotNull List<? extends LibraryOrderEntry> orderEntriesContainingFile) {
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

        GradleModuleData gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module);
        if (gradleModuleData == null)  return ActionCallback.REJECTED;

        final String gradlePath = gradleModuleData.getGradlePath();

        String sourceArtifactNotation = getSourcesArtifactNotation(artifactCoordinates, artifactIdCandidate -> {
          VirtualFile[] rootFiles = libraryOrderEntry.getRootFiles(OrderRootType.CLASSES);
          return rootFiles.length == 0 || ContainerUtil.exists(rootFiles, file -> file.getName().startsWith(artifactIdCandidate));
        });
        final String sourcesLocationFilePath;
        final File sourcesLocationFile;
        try {
          sourcesLocationFile = new File(FileUtil.createTempDirectory("sources", "loc"), "path.tmp");
          sourcesLocationFilePath = StringUtil.escapeBackSlashes(sourcesLocationFile.getCanonicalPath());
          Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtil.delete(sourcesLocationFile), "GradleAttachSourcesProvider cleanup"));
        }
        catch (IOException e) {
          GradleLog.LOG.warn(e);
          return ActionCallback.REJECTED;
        }
        final String taskName = "DownloadSources";
        // @formatter:off
        String initScript = "allprojects {\n" +
                            "  afterEvaluate { project ->\n" +
                            "    if(project.path == '" + gradlePath + "') {\n" +
                            "        def overwrite = project.tasks.findByName('" + taskName + "') != null\n" +
                            "        project.tasks.create(name: '" + taskName + "', overwrite: overwrite) {\n" +
                            "        doLast {\n" +
                            "          def configuration = null\n" +
                            "          def repository = project.repositories.toList().find {\n" +
                            "              logger.lifecycle('Attempt to download sources from ' + it.name)\n" +
                            "              project.repositories.clear()\n" +
                            "              project.repositories.add(it)\n" +
                            "              configuration = project.configurations.create('downloadSourcesFrom_' + UUID.randomUUID())\n" +
                            "              configuration.transitive = false\n" +
                            "              project.dependencies.add(configuration.name, '" + sourceArtifactNotation + "')\n" +
                            "              def files = null\n" +
                            "              try {\n" +
                            "                files = configuration.resolvedConfiguration.lenientConfiguration.getFiles()\n" +
                            "              } catch (java.lang.Throwable ignore) { }\n" +
                            "              return files && !files.isEmpty()\n" +
                            "          }\n" +
                            "          if (!repository) {\n" +
                            "              configuration = project.configurations.create('downloadSources_' + UUID.randomUUID())\n" +
                            "              configuration.transitive = false\n" +
                            "              project.dependencies.add(configuration.name, '" + sourceArtifactNotation + "')\n" +
                            "              configuration.resolve()\n" +
                            "          }\n" +
                            "          def sourcesPath = configuration?.singleFile?.path\n" +
                            "          if (sourcesPath) {\n" +
                            "            logger.lifecycle('Sources were downloaded to ' + sourcesPath)\n" +
                            "            new File('" + sourcesLocationFilePath + "').write sourcesPath\n" +
                            "          } else throw new RuntimeException('Sources download failed')\n" +
                            "        }\n" +
                            "      }\n" +
                            "    }\n" +
                            "  }\n" +
                            "}\n";
        // @formatter:on
        UserDataHolderBase userData = new UserDataHolderBase();
        userData.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, initScript);

        String gradleVmOptions = GradleSettings.getInstance(project).getGradleVmOptions();
        ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
        settings.setExecutionName(getName());
        settings.setExternalProjectPath(gradleModuleData.getDirectoryToRunTask());
        settings.setTaskNames(List.of(gradleModuleData.getTaskPath(taskName, true)));
        settings.setVmOptions(gradleVmOptions);
        settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
        ExternalSystemUtil.runTask(
          settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
          new TaskCallback() {
            @Override
            public void onSuccess() {
              VirtualFile classesFile = libraryOrderEntry.getRootFiles(OrderRootType.CLASSES)[0];
              File sourceJar = getSourceFile(artifactCoordinates, classesFile, project, settings.getExternalProjectPath());
              if (sourceJar == null) {
                try {
                  sourceJar = new File(FileUtil.loadFile(sourcesLocationFile));
                  FileUtil.delete(sourcesLocationFile);
                }
                catch (IOException e) {
                  GradleLog.LOG.warn(e);
                }
              }
              File finalSourceJar = sourceJar;
              ApplicationManager.getApplication().invokeLater(() -> {
                final Set<Library> libraries = new HashSet<>();
                for (LibraryOrderEntry orderEntry : orderEntries) {
                  ContainerUtil.addIfNotNull(libraries, orderEntry.getLibrary());
                }
                if (finalSourceJar != null) {
                  attachSourceJar(finalSourceJar, libraries);
                }
                resultWrapper.setDone();
              });
            }

            @Override
            public void onFailure() {
              resultWrapper.setRejected();
              String title = GradleBundle.message("gradle.notifications.sources.download.failed.title");
              String message = GradleBundle.message("gradle.notifications.sources.download.failed.content", artifactCoordinates);
              NotificationData notification =
                new NotificationData(title, message, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC);
              notification.setBalloonNotification(true);
              ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification);
            }
          }, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false, userData);

        return resultWrapper;
      }
    });
  }

  @VisibleForTesting
  static @NotNull String getSourcesArtifactNotation(@NotNull String artifactCoordinates,
                                                    @NotNull Predicate<String> artifactIdChecker) {
    String groupNameVersionCoordinates;
    String[] split = artifactCoordinates.split(":");
    if (split.length == 4) {
      // group:name:packaging:classifier || name:packaging:classifier:version || group:name:classifier:version || group:name:packaging:version
      boolean isArtifactId = artifactIdChecker.test(split[1]);
      groupNameVersionCoordinates = isArtifactId ? split[0] + ":" + split[1] + ":" + split[3] : artifactCoordinates;
    }
    else if (split.length == 5) {
      // group:name:packaging:classifier:version
      groupNameVersionCoordinates = split[0] + ":" + split[1] + ":" + split[4];
    }
    else {
      groupNameVersionCoordinates = artifactCoordinates;
    }
    return groupNameVersionCoordinates + ":sources";
  }

  private static @Nullable File getSourceFile(@NotNull String artifactCoordinates,
                                              VirtualFile classesFile,
                                              @NotNull Project project,
                                              @NotNull @NlsSafe String projectPath) {
    LibraryData data = new LibraryData(GradleConstants.SYSTEM_ID, artifactCoordinates);
    data.addPath(LibraryPathType.BINARY, VfsUtil.getLocalFile(classesFile).getPath());
    BuildLayoutParameters buildLayoutParameters = GradleInstallationManager.getInstance().guessBuildLayoutParameters(project, projectPath);
    String gradleUserHome = GradleTargetUtil.maybeGetLocalValue(buildLayoutParameters.getGradleUserHome());
    if (gradleUserHome == null) return null;
    attachSourcesAndJavadocFromGradleCacheIfNeeded(new File(gradleUserHome), data);
    Iterator<String> iterator = data.getPaths(LibraryPathType.SOURCE).iterator();
    return iterator.hasNext() ? new File(iterator.next()) : null;
  }

  private static @NotNull Map<LibraryOrderEntry, Module> getGradleModules(@NotNull List<? extends LibraryOrderEntry> libraryOrderEntries) {
    Map<LibraryOrderEntry, Module> result = new HashMap<>();
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
