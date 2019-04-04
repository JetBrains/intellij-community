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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogFileHistoryProvider;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.assertNotNull;


public class TabbedShowHistoryAction extends AbstractVcsAction implements UpdateInBackground {
  @Override
  protected void update(@NotNull VcsContext context, @NotNull Presentation presentation) {
    Project project = context.getProject();

    presentation.setEnabled(isEnabled(context));
    presentation.setVisible(project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss());
  }

  protected boolean isEnabled(@NotNull VcsContext context) {
    boolean result = false;
    Project project = context.getProject();

    if (project != null) {
      Pair<FilePath, VirtualFile> pair = getPathAndParentFile(context);

      if (pair.first != null && pair.second != null) {
        result = isEnabled(project, pair.first, pair.second);
      }
    }

    return result;
  }

  private static boolean isEnabled(@NotNull Project project, @NotNull FilePath path, @NotNull VirtualFile fileOrParent) {
    boolean fileInVcs = AbstractVcs.fileInVcsByFileStatus(project, fileOrParent);
    if (!fileInVcs) return false;

    AbstractVcs vcs = ChangesUtil.getVcsForFile(fileOrParent, project);
    if (vcs == null) return false;

    return canShowNewFileHistory(project, path) || canShowOldFileHistory(vcs, path, fileOrParent);
  }

  private static boolean canShowOldFileHistory(@NotNull AbstractVcs vcs, @NotNull FilePath path, @NotNull VirtualFile fileOrParent) {
    VcsHistoryProvider provider = vcs.getVcsHistoryProvider();
    return provider != null &&
           (provider.supportsHistoryForDirectories() || !path.isDirectory()) &&
           provider.canShowHistoryFor(fileOrParent);
  }

  private static boolean canShowNewFileHistory(@NotNull Project project, @NotNull FilePath path) {
    VcsLogFileHistoryProvider historyProvider = ServiceManager.getService(VcsLogFileHistoryProvider.class);
    return historyProvider != null && historyProvider.canShowFileHistory(project, path);
  }

  @NotNull
  private static Pair<FilePath, VirtualFile> getPathAndParentFile(@NotNull VcsContext context) {
    List<VirtualFile> selectedFiles = context.getSelectedFilesStream().limit(2).collect(Collectors.toList());
    if (selectedFiles.size() > 0) {
      if (selectedFiles.size() != 1) return Pair.empty();
      VirtualFile file = selectedFiles.get(0);
      return Pair.create(VcsUtil.getFilePath(file), file);
    }

    File[] ioFiles = context.getSelectedIOFiles();
    if (ioFiles != null && ioFiles.length > 0) {
      for (File ioFile : ioFiles) {
        VirtualFile parent = getParentVirtualFile(ioFile);
        if (parent != null) return Pair.create(VcsUtil.getFilePath(parent, ioFile.getName()), parent);
      }
    }

    return Pair.empty();
  }

  @Nullable
  private static VirtualFile getParentVirtualFile(@NotNull File ioFile) {
    File parentIoFile = ioFile.getParentFile();
    return parentIoFile != null ? LocalFileSystem.getInstance().findFileByIoFile(parentIoFile) : null;
  }

  @Override
  protected void actionPerformed(@NotNull VcsContext context) {
    Project project = assertNotNull(context.getProject());
    Pair<FilePath, VirtualFile> pair = getPathAndParentFile(context);
    FilePath path = assertNotNull(pair.first);
    VirtualFile fileOrParent = assertNotNull(pair.second);
    AbstractVcs vcs = assertNotNull(ChangesUtil.getVcsForFile(fileOrParent, project));

    if (canShowNewFileHistory(project, path)) {
      showNewFileHistory(project, path);
    }
    else {
      showOldFileHistory(project, vcs, path);
    }
  }

  private static void showNewFileHistory(@NotNull Project project, @NotNull FilePath path) {
    VcsLogFileHistoryProvider historyProvider = ServiceManager.getService(VcsLogFileHistoryProvider.class);
    historyProvider.showFileHistory(project, path, null);
  }

  private static void showOldFileHistory(@NotNull Project project, @NotNull AbstractVcs vcs, @NotNull FilePath path) {
    VcsHistoryProvider provider = assertNotNull(vcs.getVcsHistoryProvider());
    AbstractVcsHelper.getInstance(project).showFileHistory(provider, vcs.getAnnotationProvider(), path, vcs);
  }
}
