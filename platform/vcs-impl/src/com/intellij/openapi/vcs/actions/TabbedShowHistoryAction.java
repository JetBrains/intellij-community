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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;


public class TabbedShowHistoryAction extends AbstractVcsAction {
  @Override
  protected void update(VcsContext context, Presentation presentation) {
    presentation.setEnabled(isEnabled(context));
    final Project project = context.getProject();
    presentation.setVisible(project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss());
  }

  protected VcsHistoryProvider getProvider(AbstractVcs activeVcs) {
    return activeVcs.getVcsHistoryProvider();
  }

  protected boolean isEnabled(VcsContext context) {
    FilePath selectedFile = getSelectedFileOrNull(context);
    if (selectedFile == null) return false;
    Project project = context.getProject();
    if (project == null) return false;
    VirtualFile someVFile = selectedFile.getVirtualFile() != null ?
                            selectedFile.getVirtualFile() : selectedFile.getVirtualFileParent();
    if (someVFile == null) {
      return false;
    }
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(someVFile);
    if (vcs == null) return false;
    VcsHistoryProvider vcsHistoryProvider = getProvider(vcs);
    if (vcsHistoryProvider == null) return false;
    if (selectedFile.isDirectory() && (! vcsHistoryProvider.supportsHistoryForDirectories())) return false;
    if (!canFileHaveHistory(project, someVFile)) {
      return false;
    }
    return vcsHistoryProvider.canShowHistoryFor(someVFile);
  }

  private static boolean canFileHaveHistory(@NotNull Project project, @NotNull VirtualFile file) {
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
    return fileStatus != FileStatus.ADDED && fileStatus != FileStatus.UNKNOWN && fileStatus != FileStatus.IGNORED;
  }

  @Nullable
  protected static FilePath getSelectedFileOrNull(VcsContext context) {
    FilePath result = null;
    VirtualFile[] virtualFileArray = context.getSelectedFiles();
    if (virtualFileArray.length != 0) {
      if (virtualFileArray.length > 1) return null;
      if (virtualFileArray.length > 0) {
        result = VcsUtil.getFilePath(virtualFileArray[0]);
      }
    }

    File[] fileArray = context.getSelectedIOFiles();
    if (fileArray != null && fileArray.length > 0) {
      for (File file : fileArray) {
        final File parentIoFile = file.getParentFile();
        if (parentIoFile == null) continue;
        final VirtualFile parent = LocalFileSystem.getInstance().findFileByIoFile(parentIoFile);
        if (parent != null) {
          FilePath child = VcsUtil.getFilePath(parent, file.getName());
          if (result != null) return null;
          result = child;
        }
      }
    }
    return result;
  }

  @Override
  protected void actionPerformed(@NotNull VcsContext context) {
    FilePath path = getSelectedFileOrNull(context);
    if (path == null) return;
    Project project = context.getProject();
    VirtualFile someVFile = path.getVirtualFile() != null ? path.getVirtualFile() : path.getVirtualFileParent();
    AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(someVFile);
    assert activeVcs != null;
    VcsHistoryProvider vcsHistoryProvider = getProvider(activeVcs);
    AbstractVcsHelper.getInstance(project).showFileHistory(vcsHistoryProvider, activeVcs.getAnnotationProvider(), path, null, activeVcs);
  }



  @Override
  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }
}
