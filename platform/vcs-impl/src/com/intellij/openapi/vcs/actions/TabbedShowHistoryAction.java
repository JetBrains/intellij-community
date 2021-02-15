// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.vcs.log.VcsLogFileHistoryProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;


public class TabbedShowHistoryAction extends DumbAwareAction implements UpdateInBackground {
  private static final int MANY_CHANGES_THRESHOLD = 1000;

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean isVisible = project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss();
    e.getPresentation().setEnabled(isEnabled(e.getDataContext()));
    e.getPresentation().setVisible(isVisible);
  }

  protected boolean isEnabled(@NotNull DataContext context) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    if (project == null) return false;

    List<FilePath> selectedFiles = VcsContextUtil.selectedFilePathsIterable(context)
      .take(MANY_CHANGES_THRESHOLD)
      .toList();

    if (selectedFiles.isEmpty()) return false;

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
    VcsLogFileHistoryProvider historyProvider = ApplicationManager.getApplication().getService(VcsLogFileHistoryProvider.class);
    return historyProvider != null && historyProvider.canShowFileHistory(project, paths, null);
  }

  @Nullable
  private static VirtualFile getExistingFileOrParent(@NotNull FilePath selectedPath) {
    return ObjectUtils.chooseNotNull(selectedPath.getVirtualFile(), selectedPath.getVirtualFileParent());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = Objects.requireNonNull(e.getProject());
    List<FilePath> selectedFiles = VcsContextUtil.selectedFilePaths(e.getDataContext());
    if (canShowNewFileHistory(project, selectedFiles)) {
      showNewFileHistory(project, selectedFiles);
    }
    else if (selectedFiles.size() == 1) {
      FilePath path = Objects.requireNonNull(ContainerUtil.getFirstItem(selectedFiles));
      AbstractVcs vcs = Objects.requireNonNull(ChangesUtil.getVcsForFile(Objects.requireNonNull(getExistingFileOrParent(path)), project));
      showOldFileHistory(project, vcs, path);
    }
  }

  private static void showNewFileHistory(@NotNull Project project, @NotNull Collection<FilePath> paths) {
    VcsLogFileHistoryProvider historyProvider = ApplicationManager.getApplication().getService(VcsLogFileHistoryProvider.class);
    historyProvider.showFileHistory(project, paths, null);
  }

  private static void showOldFileHistory(@NotNull Project project, @NotNull AbstractVcs vcs, @NotNull FilePath path) {
    VcsHistoryProvider provider = Objects.requireNonNull(vcs.getVcsHistoryProvider());
    AbstractVcsHelper.getInstance(project).showFileHistory(provider, vcs.getAnnotationProvider(), path, vcs);
  }
}
