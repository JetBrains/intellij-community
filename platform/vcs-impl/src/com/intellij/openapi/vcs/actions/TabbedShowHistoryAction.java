/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.components.ServiceManager;
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
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.assertNotNull;


public class TabbedShowHistoryAction extends AbstractVcsAction implements UpdateInBackground {
  private static final long MANY_CHANGES_THRESHOLD = 1000;

  @Override
  protected void update(@NotNull VcsContext context, @NotNull Presentation presentation) {
    Project project = context.getProject();

    presentation.setEnabled(isEnabled(context));
    presentation.setVisible(project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss());
  }

  protected boolean isEnabled(@NotNull VcsContext context) {
    Project project = context.getProject();
    if (project == null) return false;

    List<FilePath> selectedFiles = context.getSelectedFilePathsStream().limit(MANY_CHANGES_THRESHOLD)
      .collect(Collectors.toList());

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
    VcsLogFileHistoryProvider historyProvider = ServiceManager.getService(VcsLogFileHistoryProvider.class);
    return historyProvider != null && historyProvider.canShowFileHistory(project, paths, null);
  }

  @Nullable
  private static VirtualFile getExistingFileOrParent(@NotNull FilePath selectedPath) {
    return ObjectUtils.chooseNotNull(selectedPath.getVirtualFile(), selectedPath.getVirtualFileParent());
  }

  @Override
  protected void actionPerformed(@NotNull VcsContext context) {
    Project project = assertNotNull(context.getProject());
    List<FilePath> selectedFiles = context.getSelectedFilePathsStream().collect(Collectors.toList());
    if (canShowNewFileHistory(project, selectedFiles)) {
      showNewFileHistory(project, selectedFiles);
    }
    else if (selectedFiles.size() == 1) {
      FilePath path = assertNotNull(ContainerUtil.getFirstItem(selectedFiles));
      AbstractVcs vcs = assertNotNull(ChangesUtil.getVcsForFile(assertNotNull(getExistingFileOrParent(path)), project));
      showOldFileHistory(project, vcs, path);
    }
  }

  private static void showNewFileHistory(@NotNull Project project, @NotNull Collection<FilePath> paths) {
    VcsLogFileHistoryProvider historyProvider = ServiceManager.getService(VcsLogFileHistoryProvider.class);
    historyProvider.showFileHistory(project, paths, null);
  }

  private static void showOldFileHistory(@NotNull Project project, @NotNull AbstractVcs vcs, @NotNull FilePath path) {
    VcsHistoryProvider provider = assertNotNull(vcs.getVcsHistoryProvider());
    AbstractVcsHelper.getInstance(project).showFileHistory(provider, vcs.getAnnotationProvider(), path, vcs);
  }
}
