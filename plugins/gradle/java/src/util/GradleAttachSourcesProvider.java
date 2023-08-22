// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util;

import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder;
import org.jetbrains.plugins.gradle.service.execution.GradleInitScriptUtil;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.service.task.LazyVersionSpecificInitScript;
import org.jetbrains.plugins.gradle.service.task.VersionSpecificInitScript;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Predicate;

import static com.intellij.jarFinder.InternetAttachSourceProvider.attachSourceJar;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.collectSourcesAndJavadocsFor;

/**
 * @author Vladislav.Soroka
 */
final class GradleAttachSourcesProvider implements AttachSourcesProvider {

  private static final GradleVersion GRADLE_5_6 = GradleVersion.version("5.6");
  private static final String ANDROID_LIBRARY_SUFFIX = "@aar";
  private static final String INIT_SCRIPT_FILE_PREFIX = "ijDownloadSources";

  @Override
  public @NotNull Collection<? extends AttachSourcesAction> getActions(@NotNull List<? extends LibraryOrderEntry> orderEntries,
                                                                       @NotNull PsiFile psiFile) {
    Map<LibraryOrderEntry, Module> gradleModules = getGradleModules(orderEntries);
    if (gradleModules.isEmpty()) {
      return List.of();
    }
    return List.of(new GradleDownloadSourceAction(orderEntries, psiFile));
  }

  private static class GradleDownloadSourceAction implements AttachSourcesAction {

    private final @NotNull List<? extends LibraryOrderEntry> orderEntries;
    private final @NotNull PsiFile psiFile;

    private GradleDownloadSourceAction(@NotNull List<? extends LibraryOrderEntry> orderEntries, @NotNull PsiFile psiFile) {
      this.orderEntries = orderEntries;
      this.psiFile = psiFile;
    }

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
      Pair<String, LibraryOrderEntry> artifactMetadata = getArtifactMetadata(orderEntries);
      if (artifactMetadata == null) {
        return ActionCallback.REJECTED;
      }
      String artifactCoordinates = artifactMetadata.getFirst();
      LibraryOrderEntry libraryOrderEntry = artifactMetadata.getSecond();
      String cachedSourcesPath = lookupSourcesPathFromCache(libraryOrderEntry);
      if (cachedSourcesPath != null && isValidJar(cachedSourcesPath)) {
        attachSources(new File(cachedSourcesPath), orderEntries);
        return ActionCallback.DONE;
      }
      String sourceArtifactNotation = getSourcesArtifactNotation(artifactCoordinates, artifactIdCandidate -> {
        VirtualFile[] rootFiles = libraryOrderEntry.getRootFiles(OrderRootType.CLASSES);
        return rootFiles.length == 0 || ContainerUtil.exists(rootFiles, file -> file.getName().startsWith(artifactIdCandidate));
      });
      return downloadSources(psiFile, sourceArtifactNotation, artifactCoordinates);
    }

    private static @Nullable String lookupSourcesPathFromCache(@NotNull LibraryOrderEntry libraryOrderEntry) {
      VirtualFile[] rootFiles = libraryOrderEntry.getRootFiles(OrderRootType.CLASSES);
      if (rootFiles.length == 0) {
        return null;
      }
      VirtualFile classesFile = rootFiles[0];
      String path = classesFile.getPath();
      if (path.contains("/caches/transforms-")) {
        return null;
      }
      Map<LibraryPathType, List<String>> target = new HashMap<>();
      collectSourcesAndJavadocsFor(path, target, /*source resolved*/ false, /*javadoc resolved*/ true);
      List<String> sources = target.get(LibraryPathType.SOURCE);
      if (sources == null || sources.isEmpty()) {
        return null;
      }
      return sources.iterator().next();
    }

    private @NotNull ActionCallback downloadSources(@NotNull PsiFile psiFile,
                                           @NotNull String sourceArtifactNotation,
                                           @NotNull String artifactCoordinates) {
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
      Project project = psiFile.getProject();
      String gradleProjectRoot = getGradleProjectRoot(project);
      if (gradleProjectRoot == null) {
        GradleLog.LOG.warn("Unable to find root folder for Gradle project");
        return ActionCallback.REJECTED;
      }
      String taskName = "ijDownloadSources" + UUID.randomUUID().toString().substring(0, 12);
      ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
      settings.setExecutionName(getName());
      settings.setExternalProjectPath(gradleProjectRoot);
      settings.setTaskNames(List.of(taskName));
      settings.setVmOptions(GradleSettings.getInstance(project).getGradleVmOptions());
      settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());

      UserDataHolderBase userData = prepareUserData(sourceArtifactNotation, taskName, sourcesLocationFilePath);
      final ActionCallback resultWrapper = new ActionCallback();
      ExternalSystemUtil.runTask(
        settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
        new TaskCallback() {
          @Override
          public void onSuccess() {
            File sourceJar;
            try {
              String downloadedArtifactPath = FileUtil.loadFile(sourcesLocationFile);
              if (!isValidJar(downloadedArtifactPath)) {
                GradleLog.LOG.warn("Incorrect file header: " + downloadedArtifactPath + ". Unable to process downloaded file as a JAR file");
                FileUtil.delete(sourcesLocationFile);
                resultWrapper.setRejected();
                return;
              }
              sourceJar = new File(downloadedArtifactPath);
              FileUtil.delete(sourcesLocationFile);
            }
            catch (IOException e) {
              GradleLog.LOG.warn(e);
              resultWrapper.setRejected();
              return;
            }
            attachSources(sourceJar, orderEntries);
            resultWrapper.setDone();
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

    private static void attachSources(@NotNull File sourcesJar, @NotNull List<? extends LibraryOrderEntry> orderEntries) {
      ApplicationManager.getApplication()
        .invokeLater(() -> {
          final Set<Library> libraries = new HashSet<>();
          for (LibraryOrderEntry orderEntry : orderEntries) {
            ContainerUtil.addIfNotNull(libraries, orderEntry.getLibrary());
          }
          attachSourceJar(sourcesJar, libraries);
        });
    }
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
    return StringUtil.trimEnd(groupNameVersionCoordinates, ANDROID_LIBRARY_SUFFIX) + ":sources";
  }

  private static @NotNull UserDataHolderBase prepareUserData(@NotNull String sourceArtifactNotation,
                                                    @NotNull String taskName,
                                                    @NotNull String sourcesLocationFilePath
  ) {
    UserDataHolderBase userData = new UserDataHolderBase();
    VersionSpecificInitScript legacyInitScript = new LazyVersionSpecificInitScript(
      () -> GradleInitScriptUtil.loadLegacyDownloadSourcesInitScript(sourceArtifactNotation, taskName, sourcesLocationFilePath),
      INIT_SCRIPT_FILE_PREFIX,
      version -> GRADLE_5_6.compareTo(version) > 0
    );
    VersionSpecificInitScript initScript = new LazyVersionSpecificInitScript(
      () -> GradleInitScriptUtil.loadDownloadSourcesInitScript(sourceArtifactNotation, taskName, sourcesLocationFilePath),
      INIT_SCRIPT_FILE_PREFIX,
      version -> GRADLE_5_6.compareTo(version) <= 0
    );
    userData.putUserData(GradleTaskManager.VERSION_SPECIFIC_SCRIPTS_KEY, List.of(legacyInitScript, initScript));
    return userData;
  }

  private static @Nullable String getGradleProjectRoot(@NotNull Project project) {
    return GradleSettings.getInstance(project)
      .getLinkedProjectsSettings()
      .stream()
      .findFirst()
      .map(ExternalProjectSettings::getExternalProjectPath)
      .orElse(null);
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

  private static @Nullable Pair<String, LibraryOrderEntry> getArtifactMetadata(@NotNull List<? extends LibraryOrderEntry> orderEntries) {
    Map<LibraryOrderEntry, Module> gradleModules = getGradleModules(orderEntries);
    if (gradleModules.isEmpty()) {
      return null;
    }
    Map.Entry<LibraryOrderEntry, Module> next = gradleModules.entrySet().iterator().next();
    Module module = next.getValue();
    if (CachedModuleDataFinder.getGradleModuleData(module) == null) {
      return null;
    }
    LibraryOrderEntry libraryOrderEntry = next.getKey();
    String libraryName = libraryOrderEntry.getLibraryName();
    if (libraryName == null) {
      return null;
    }
    String artifactCoordinates = StringUtil.trimStart(libraryName, GradleConstants.SYSTEM_ID.getReadableName() + ": ");
    if (StringUtil.equals(libraryName, artifactCoordinates)) {
      return null;
    }
    return new Pair<>(artifactCoordinates, libraryOrderEntry);
  }

  private static boolean isValidJar(@NotNull String rawPath) {
    try (InputStream is = Files.newInputStream(Path.of(rawPath), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      byte[] head = is.readNBytes(2);
      if (head.length < 2) {
        return false;
      }
      return head[0] == 0x50 && head[1] == 0x4b;
    }
    catch (IOException e) {
      return false;
    }
  }
}
