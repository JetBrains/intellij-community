// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil;

import com.intellij.diff.DiffContentFactoryImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.util.paths.RecursiveFilePathSet;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.nio.charset.Charset;
import java.util.Collection;

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

  public static @NlsSafe @NotNull String getShortVcsRootName(@NotNull Project project, @NotNull VirtualFile root) {
    return VcsUtil.getShortVcsRootName(project, root);
  }

  public static @Nullable IgnoredFileContentProvider findIgnoredFileContentProvider(@NotNull AbstractVcs vcs) {
    return findIgnoredFileContentProvider(vcs.getProject(), vcs.getKeyInstanceMethod());
  }

  public static @Nullable IgnoredFileContentProvider findIgnoredFileContentProvider(@NotNull Project project, @NotNull VcsKey vcsKey) {
    IgnoredFileContentProvider ignoreContentProvider = null;
    for (IgnoredFileContentProvider provider : IgnoredFileContentProvider.IGNORE_FILE_CONTENT_PROVIDER.getExtensionList(project)) {
      if (provider.getSupportedVcs().equals(vcsKey)) {
        ignoreContentProvider = provider;
        break;
      }
    }

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

  private static boolean isFileSharedInVcs(@NotNull Project project,
                                           @NotNull ChangeListManager changeListManager,
                                           @NotNull String filePath) {
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

  public static @NotNull String loadTextFromBytes(@Nullable Project project, byte @NotNull [] bytes, @NotNull FilePath filePath) {
    Charset charset = DiffContentFactoryImpl.guessCharset(project, bytes, filePath);
    return CharsetToolkit.decodeString(bytes, charset);
  }

  public static @Nullable VirtualFile findValidParentAccurately(@NotNull FilePath filePath) {
    VirtualFile result = filePath.getVirtualFile();
    if (result != null) return result;

    String path = filePath.getPath();
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      result = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      if (result != null) return result;
    }

    Pair<NewVirtualFile, NewVirtualFile> pair = NewVirtualFileSystem.findCachedFileByPath(LocalFileSystem.getInstance(), path);
    return pair.first != null ? pair.first : pair.second;
  }

  public static @NotNull JBIterable<? extends Change> filterChangesUnderFiles(@NotNull Iterable<? extends Change> changes,
                                                                              @NotNull Collection<VirtualFile> files) {
    return filterChangesUnder(changes, ContainerUtil.map(files, file -> VcsUtil.getFilePath(file)));
  }

  public static @NotNull JBIterable<? extends Change> filterChangesUnder(@NotNull Iterable<? extends Change> changes,
                                                                         @NotNull Collection<FilePath> filePaths) {
    if (filePaths.isEmpty()) return JBIterable.empty();

    RecursiveFilePathSet scope = new RecursiveFilePathSet(SystemInfoRt.isFileSystemCaseSensitive);
    scope.addAll(filePaths);

    return JBIterable.from(changes).filter(change -> isUnderScope(scope, change));
  }

  private static boolean isUnderScope(@NotNull RecursiveFilePathSet scope, @NotNull Change change) {
    FilePath beforePath = ChangesUtil.getBeforePath(change);
    if (beforePath != null &&
        scope.hasAncestor(beforePath)) {
      return true;
    }

    FilePath afterPath = ChangesUtil.getAfterPath(change);
    if (afterPath != null &&
        !ChangesUtil.equalsCaseSensitive(beforePath, afterPath) &&
        scope.hasAncestor(afterPath)) {
      return true;
    }

    return false;
  }
}