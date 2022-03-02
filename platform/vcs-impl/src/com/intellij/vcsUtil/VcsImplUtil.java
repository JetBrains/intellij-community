// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.diff.DiffContentFactoryImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.IgnoredFileContentProvider;
import com.intellij.openapi.vcs.changes.IgnoredFileGenerator;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.nio.charset.Charset;

import static com.intellij.openapi.vcs.FileStatus.IGNORED;
import static com.intellij.openapi.vcs.FileStatus.UNKNOWN;
import static com.intellij.vcsUtil.VcsUtil.isFileUnderVcs;

/**
 * <p>{@link VcsUtil} extension that needs access to the {@code intellij.platform.vcs.impl} module.</p>
 */
public final class VcsImplUtil {

  private static final Logger LOG = Logger.getInstance(VcsImplUtil.class);

  /**
   * Shows error message with specified message text and title.
   * The parent component is the root frame.
   *
   * @param project Current project component
   * @param message information message
   * @param title   Dialog title
   */
  public static void showErrorMessage(final Project project, @DialogMessage String message, @DialogTitle String title) {
    Runnable task = () -> Messages.showErrorDialog(project, message, title);
    WaitForProgressToShow.runOrInvokeLaterAboveProgress(task, null, project);
  }

  @NlsSafe
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

  @Nullable
  public static IgnoredFileContentProvider findIgnoredFileContentProvider(@NotNull AbstractVcs vcs) {
    return findIgnoredFileContentProvider(vcs.getProject(), vcs.getKeyInstanceMethod());
  }

  @Nullable
  public static IgnoredFileContentProvider findIgnoredFileContentProvider(@NotNull Project project, @NotNull VcsKey vcsKey) {
    IgnoredFileContentProvider ignoreContentProvider = IgnoredFileContentProvider.IGNORE_FILE_CONTENT_PROVIDER.extensions(project)
      .filter((provider) -> provider.getSupportedVcs().equals(vcsKey))
      .findFirst()
      .orElse(null);

    if (ignoreContentProvider == null) {
      LOG.debug("Cannot get ignore content provider for vcs " + vcsKey.getName());
      return null;
    }
    return ignoreContentProvider;
  }

  public static void proposeUpdateIgnoreFile(@NotNull Project project,
                                             @NotNull AbstractVcs vcs,
                                             @NotNull VirtualFile ignoreFileRoot) {
    generateIgnoreFile(project, vcs, ignoreFileRoot, true);
  }

  public static void generateIgnoreFileIfNeeded(@NotNull Project project,
                                                @NotNull AbstractVcs vcs,
                                                @NotNull VirtualFile ignoreFileRoot) {
    generateIgnoreFile(project, vcs, ignoreFileRoot, false);
  }

  private static void generateIgnoreFile(@NotNull Project project,
                                         @NotNull AbstractVcs vcs,
                                         @NotNull VirtualFile ignoreFileRoot, boolean notify) {
    IgnoredFileGenerator ignoredFileGenerator = project.getService(IgnoredFileGenerator.class);
    if (ignoredFileGenerator == null) {
      LOG.debug("Cannot find ignore file ignoredFileGenerator for " + vcs.getName() + " VCS");
      return;
    }
    ignoredFileGenerator.generateFile(ignoreFileRoot, vcs, notify);
  }

  private static boolean isFileSharedInVcs(@NotNull Project project, @NotNull ChangeListManager changeListManager, @NotNull String filePath) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (file == null) return false;
    FileStatus fileStatus = changeListManager.getStatus(file);
    return isFileUnderVcs(project, filePath) &&
           (fileStatus != UNKNOWN && fileStatus != IGNORED);
  }

  public static boolean isProjectSharedInVcs(@NotNull Project project) {
    return ReadAction.compute(() -> {
      if (project.isDisposed()) return false;
      @SystemIndependent String projectFilePath = project.getProjectFilePath();
      ChangeListManagerEx changeListManager = ChangeListManagerEx.getInstanceEx(project);
      return !changeListManager.isInUpdate()
             && (projectFilePath != null && isFileSharedInVcs(project, changeListManager, projectFilePath));
    });
  }

  @NotNull
  public static String loadTextFromBytes(@Nullable Project project, byte @NotNull [] bytes, @NotNull FilePath filePath) {
    Charset charset = DiffContentFactoryImpl.guessCharset(project, bytes, filePath);
    return CharsetToolkit.decodeString(bytes, charset);
  }
}