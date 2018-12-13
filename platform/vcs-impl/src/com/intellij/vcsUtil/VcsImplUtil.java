// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.IgnoredFileContentProvider;
import com.intellij.openapi.vcs.changes.IgnoredFileGenerator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * <p>{@link VcsUtil} extension that needs access to the {@code intellij.platform.vcs.impl} module.</p>
 */
public class VcsImplUtil {

  private static final Logger LOG = Logger.getInstance(VcsImplUtil.class);

  public static final String MANAGE_IGNORE_FILES_PROPERTY = "MANAGE_IGNORE_FILES_PROPERTY";

  /**
   * Shows error message with specified message text and title.
   * The parent component is the root frame.
   *
   * @param project Current project component
   * @param message information message
   * @param title   Dialog title
   */
  public static void showErrorMessage(final Project project, final String message, final String title) {
    Runnable task = () -> Messages.showErrorDialog(project, message, title);
    WaitForProgressToShow.runOrInvokeLaterAboveProgress(task, null, project);
  }

  @NotNull
  public static String getShortVcsRootName(@NotNull Project project, @NotNull VirtualFile root) {
    VirtualFile projectDir = project.getBaseDir();

    String repositoryPath = root.getPresentableUrl();
    if (projectDir != null) {
      String relativePath = VfsUtilCore.getRelativePath(root, projectDir, File.separatorChar);
      if (relativePath != null) {
        repositoryPath = relativePath;
      }
    }

    return repositoryPath.isEmpty() ? root.getName() : repositoryPath;
  }

  public static boolean isNonModalCommit() {
    return Registry.is("vcs.non.modal.commit");
  }

  public static void proposeUpdateIgnoreFile(@NotNull Project project,
                                             @NotNull AbstractVcs vcs,
                                             @NotNull VirtualFile ignoreFileRoot) {
    IgnoredFileContentProvider ignoreContentProvider = getIgnoredFileContentProvider(project, vcs);

    if (ignoreContentProvider == null) {
      LOG.debug("Cannot get ignore content provider for vcs " + vcs.getName());
      return;
    }

    String ignoreFileName = ignoreContentProvider.getFileName();
    File ignoreFile = Paths.get(ignoreFileRoot.getPath(), ignoreFileName).toFile();

    if (canManageIgnoreFiles(project)) {
      updateIgnoreFileIfNeeded(project, vcs, ignoreFileRoot, ignoreFile.exists());
    }
    else {
      notifyVcsIgnoreFileManage(project, () -> updateIgnoreFileAndOpen(project, vcs, ignoreFileRoot, ignoreFile));
    }
  }

  private static void notifyVcsIgnoreFileManage(@NotNull Project project,
                                                @NotNull Runnable manageIgnore) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
    VcsApplicationSettings applicationSettings = VcsApplicationSettings.getInstance();

    VcsNotifier.getInstance(project).notifyMinorInfo(
      "",
      VcsBundle.message("ignored.file.manage.message"),
      NotificationAction.create(VcsBundle.message("ignored.file.manage.this.project"), (event, notification) -> {
        manageIgnore.run();
        propertiesComponent.setValue(MANAGE_IGNORE_FILES_PROPERTY, true);
        notification.expire();
      }),
      NotificationAction.create(VcsBundle.message("ignored.file.manage.all.project"), (event, notification) -> {
        manageIgnore.run();
        applicationSettings.MANAGE_IGNORE_FILES = true;
        notification.expire();
      }),
      NotificationAction.create(VcsBundle.message("ignored.file.manage.notnow"), (event, notification) -> {
        notification.expire();
      }));
  }

  public static boolean generateIgnoreFileIfNeeded(@NotNull Project project,
                                                   @NotNull AbstractVcs vcs,
                                                   @NotNull VirtualFile ignoreFileRoot) {
    return updateIgnoreFileIfNeeded(project, vcs, ignoreFileRoot, false);
  }

  public static boolean updateIgnoreFileIfNeeded(@NotNull Project project,
                                                 @NotNull AbstractVcs vcs,
                                                 @NotNull VirtualFile ignoreFileRoot, boolean append) {
    IgnoredFileGenerator ignoredFileGenerator = ServiceManager.getService(project, IgnoredFileGenerator.class);
    if (ignoredFileGenerator == null) {
      LOG.debug("Cannot find ignore file ignoredFileGenerator for " + vcs.getName() + " VCS");
      return false;
    }
    try {
      return append ? ignoredFileGenerator.appendFile(ignoreFileRoot, vcs) : ignoredFileGenerator.generateFile(ignoreFileRoot, vcs);
    }
    catch (IOException e) {
      LOG.warn(e);
      return false;
    }
  }

  private static void updateIgnoreFileAndOpen(@NotNull Project project,
                                                 @NotNull AbstractVcs vcs,
                                                 @NotNull VirtualFile ignoreFileRoot, @NotNull File ignoreFile) {
    if (updateIgnoreFileIfNeeded(project, vcs, ignoreFileRoot, ignoreFile.exists())) {
      VirtualFile ignoreVFile = VfsUtil.findFileByIoFile(ignoreFile, true);
      if (ignoreVFile == null) return;
      new OpenFileDescriptor(project, ignoreVFile).navigate(true);
    }
  }

  private static IgnoredFileContentProvider getIgnoredFileContentProvider(@NotNull Project project,
                                                                          @NotNull AbstractVcs vcs) {
    return IgnoredFileContentProvider.IGNORE_FILE_CONTENT_PROVIDER.extensions(project)
      .filter((provider) -> provider.getSupportedVcs().equals(vcs.getKeyInstanceMethod()))
      .findFirst()
      .orElse(null);
  }

  public static boolean canManageIgnoreFiles(@NotNull Project project) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
    VcsApplicationSettings applicationSettings = VcsApplicationSettings.getInstance();

    return applicationSettings.MANAGE_IGNORE_FILES || propertiesComponent.getBoolean(MANAGE_IGNORE_FILES_PROPERTY, false);
  }
}