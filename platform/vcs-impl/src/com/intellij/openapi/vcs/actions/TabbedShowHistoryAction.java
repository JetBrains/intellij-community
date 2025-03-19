// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.vcs.log.VcsLogFileHistoryProvider;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


public class TabbedShowHistoryAction extends DumbAwareAction {
  private static final int MANY_CHANGES_THRESHOLD = 1000;

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    DataContext context = e.getDataContext();

    boolean isVisible = project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss();
    boolean isEnabled = isEnabled(context);

    e.getPresentation().setEnabled(isEnabled);
    e.getPresentation().setVisible(isVisible);

    if (isEnabled && isVisible && VcsContextUtil.selectedFilePathsIterable(context).isEmpty()) {
      VirtualFile editorFile = getEditorFile(context);
      if (editorFile != null) {
        e.getPresentation().setText(ActionsBundle.message("action.Vcs.ShowTabbedFileHistory.for.file.text", editorFile.getName()));
      }
    }
  }

  protected boolean isEnabled(@NotNull DataContext context) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    if (project == null) return false;

    List<FilePath> selectedFiles = getSelectedPaths(context)
      .take(MANY_CHANGES_THRESHOLD)
      .toList();

    if (selectedFiles.isEmpty()) return false;

    List<FilePath> symlinkedPaths = getContextSymlinkedPaths(project, getSelectedFile(context));
    if (symlinkedPaths != null && canShowNewFileHistory(project, symlinkedPaths)) {
      return true;
    }

    if (canShowNewFileHistory(project, selectedFiles)) {
      return ContainerUtil.all(selectedFiles, path -> AbstractVcs.fileInVcsByFileStatus(project, path));
    }

    if (selectedFiles.size() == 1) {
      FilePath selectedPath = ContainerUtil.getFirstItem(selectedFiles);
      if (selectedPath == null) return false;
      VirtualFile fileOrParent = getExistingFileOrParent(selectedPath);
      if (fileOrParent == null) return false;

      if (canShowOldFileHistory(project, selectedPath, fileOrParent)) {
        return AbstractVcs.fileInVcsByFileStatus(project, selectedPath);
      }
    }

    return false;
  }

  private static boolean canShowOldFileHistory(@NotNull Project project, @NotNull FilePath path, @NotNull VirtualFile fileOrParent) {
    AbstractVcs vcs = ChangesUtil.getVcsForFile(fileOrParent, project);
    if (vcs == null) return false;

    VcsHistoryProvider provider = vcs.getVcsHistoryProvider();
    return provider != null &&
           (provider.supportsHistoryForDirectories() || !path.isDirectory()) &&
           provider.canShowHistoryFor(fileOrParent);
  }

  private static boolean canShowNewFileHistory(@NotNull Project project, @NotNull Collection<FilePath> paths) {
    VcsLogFileHistoryProvider historyProvider = project.getService(VcsLogFileHistoryProvider.class);
    return historyProvider != null && historyProvider.canShowFileHistory(paths, null);
  }

  private static @Nullable VirtualFile getExistingFileOrParent(@NotNull FilePath selectedPath) {
    return ObjectUtils.chooseNotNull(selectedPath.getVirtualFile(), selectedPath.getVirtualFileParent());
  }

  private static @Nullable List<FilePath> getContextSymlinkedPaths(@NotNull Project project, @Nullable VirtualFile file) {
    VirtualFile vcsFile = VcsUtil.resolveSymlink(project, file);
    return vcsFile != null ? Collections.singletonList(VcsUtil.getFilePath(vcsFile)) : null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    List<FilePath> symlinkedPaths = getContextSymlinkedPaths(project, getSelectedFile(e.getDataContext()));
    if (symlinkedPaths != null && canShowNewFileHistory(project, symlinkedPaths)) {
      showNewFileHistory(project, symlinkedPaths);
      return;
    }

    List<FilePath> selectedFiles = getSelectedPaths(e.getDataContext()).toList();
    if (canShowNewFileHistory(project, selectedFiles)) {
      showNewFileHistory(project, selectedFiles);
      return;
    }

    if (selectedFiles.size() == 1) {
      FilePath path = ContainerUtil.getOnlyItem(selectedFiles);
      VirtualFile fileOrParent = getExistingFileOrParent(path);
      if (fileOrParent == null) return;

      AbstractVcs vcs = ChangesUtil.getVcsForFile(fileOrParent, project);
      if (vcs == null) return;

      showOldFileHistory(project, vcs, path);
    }
  }

  private static void showNewFileHistory(@NotNull Project project, @NotNull Collection<FilePath> paths) {
    VcsLogFileHistoryProvider historyProvider = project.getService(VcsLogFileHistoryProvider.class);
    historyProvider.showFileHistory(paths, null);
  }

  private static void showOldFileHistory(@NotNull Project project, @NotNull AbstractVcs vcs, @NotNull FilePath path) {
    VcsHistoryProvider provider = Objects.requireNonNull(vcs.getVcsHistoryProvider());
    AbstractVcsHelper.getInstance(project).showFileHistory(provider, vcs.getAnnotationProvider(), path, vcs);
  }

  private static @NotNull JBIterable<FilePath> getSelectedPaths(@NotNull DataContext context) {
    JBIterable<FilePath> paths = VcsContextUtil.selectedFilePathsIterable(context);
    if (paths.isNotEmpty()) return paths;
    VirtualFile file = getEditorFile(context);
    if (file != null) return JBIterable.of(VcsUtil.getFilePath(file));
    return JBIterable.empty();
  }

  private static @Nullable VirtualFile getSelectedFile(@NotNull DataContext context) {
    VirtualFile file = VcsContextUtil.selectedFile(context);
    if (file != null) return file;
    return getEditorFile(context);
  }

  private static @Nullable VirtualFile getEditorFile(@NotNull DataContext context) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    if (project == null) return null;
    VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    if (selectedFiles.length == 0) return null;
    VirtualFile selectedFile = selectedFiles[0];
    if (!selectedFile.isInLocalFileSystem()) return null;
    return selectedFile;
  }
}
